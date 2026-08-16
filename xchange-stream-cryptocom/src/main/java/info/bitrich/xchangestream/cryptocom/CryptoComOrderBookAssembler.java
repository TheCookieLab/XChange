package info.bitrich.xchangestream.cryptocom;

import info.bitrich.xchangestream.cryptocom.dto.CryptoComOrderBookContinuityException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.knowm.xchange.cryptocom.CryptoComAdapters;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComOrderBookData;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles a consistent order book from the official Crypto.com v1 book channel snapshot /
 * increment contract.
 *
 * <p>Every dataframe carries a sequence chain: a full snapshot has {@code u} (update id, unique
 * and incremental per instrument per WebSocket session) and no {@code pu}; a partial update has
 * both {@code u} and {@code pu} and may only be applied when {@code pu} equals the {@code u} of
 * the last applied dataframe. The assembler enforces that contract:
 *
 * <ul>
 *   <li><strong>Snapshot acquisition:</strong> the first message after (re)subscription is the
 *       opening full snapshot. Increments received before it are buffered (bounded) and applied in
 *       order once the snapshot establishes the book.
 *   <li><strong>Stale/duplicate rejection:</strong> an increment whose {@code u} is not newer than
 *       the last applied {@code u} is a stale or duplicate delivery and is dropped.
 *   <li><strong>Gap detection:</strong> an increment whose {@code pu} does not chain off the last
 *       applied {@code u} breaks the sequence. A dedicated {@link
 *       CryptoComOrderBookContinuityException} is emitted on {@link #continuityFailures()} and the
 *       assembler enters {@code NEEDS_REBUILD}: the book is kept but no longer trusted until the
 *       next full snapshot.
 *   <li><strong>Rebuild:</strong> {@link #markConnectionLost()} (reconnect/re-subscription) and
 *       any full snapshot rebuild the book from scratch, resetting the sequence chain. The next
 *       snapshot arrives automatically because the framework re-subscribes the channel after each
 *       connection.
 * </ul>
 *
 * <p>Levels are {@code [price, quantity(, numberOfOrders)]}; a quantity of zero removes the price
 * level. The assembled book is trimmed to the subscribed depth on every emission.
 *
 * <p>The assembler is single-threaded: call {@link #apply(CryptoComOrderBookData)} (and {@link
 * #markConnectionLost()}) from the WebSocket event-loop thread only.
 */
public final class CryptoComOrderBookAssembler {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoComOrderBookAssembler.class);

  /** Maximum number of increments buffered while waiting for the opening snapshot. */
  static final int MAX_SNAPSHOT_BUFFER = 64;

  private final String channel;
  private final CurrencyPair currencyPair;
  private final int depth;

  private final PublishSubject<CryptoComOrderBookContinuityException> failures =
      PublishSubject.create();

  private State state = State.WAITING_SNAPSHOT;
  private Long lastAppliedSequence;
  private OrderBook book;
  private final Deque<CryptoComOrderBookData> snapshotBuffer = new ArrayDeque<>();

  public CryptoComOrderBookAssembler(String channel, CurrencyPair currencyPair, int depth) {
    if (depth <= 0) {
      throw new IllegalArgumentException("depth must be positive: " + depth);
    }
    this.channel = channel;
    this.currencyPair = currencyPair;
    this.depth = depth;
  }

  /**
   * Dedicated continuity failures; a failure is emitted once per broken-sequence episode and the
   * assembler recovers automatically on the next full snapshot.
   */
  public Observable<CryptoComOrderBookContinuityException> continuityFailures() {
    return failures;
  }

  /** Current assembled book, or {@code null} before the opening snapshot was applied. */
  public synchronized OrderBook currentBook() {
    return book == null ? null : copyOf(book);
  }

  /** True while a snapshot is required to (re)establish a trusted book. */
  public synchronized boolean needsRebuild() {
    return state == State.NEEDS_REBUILD;
  }

  /** True before any snapshot was applied for the current session. */
  public synchronized boolean awaitingSnapshot() {
    return state == State.WAITING_SNAPSHOT;
  }

  /** Last applied {@code u}, or {@code null} when no snapshot was applied yet. */
  public synchronized Long lastAppliedSequence() {
    return lastAppliedSequence;
  }

  /**
   * Marks the connection lost (drop/reconnect): the old session's sequence chain is void, the
   * book is kept but not trusted, and the next full snapshot rebuilds it.
   */
  public synchronized void markConnectionLost() {
    if (state == State.NEEDS_REBUILD) {
      return;
    }
    state = State.NEEDS_REBUILD;
    snapshotBuffer.clear();
  }

  /**
   * Applies one book dataframe. Returns the current book as an emission list (empty when the
   * dataframe was buffered, rejected or dropped) - the market data service emits the returned
   * books downstream.
   */
  public synchronized List<OrderBook> apply(CryptoComOrderBookData data) {
    if (data == null) {
      return Collections.emptyList();
    }
    boolean isSnapshot =
        data.getSequence() == null || data.getPreviousSequence() == null;
    if (isSnapshot) {
      return applySnapshot(data);
    }
    switch (state) {
      case WAITING_SNAPSHOT:
        // Increments before the opening snapshot: buffer (bounded) until it arrives.
        if (snapshotBuffer.size() >= MAX_SNAPSHOT_BUFFER) {
          failSequence(null, data.getSequence(), data.getPreviousSequence());
          snapshotBuffer.clear();
          state = State.NEEDS_REBUILD;
          return Collections.emptyList();
        }
        snapshotBuffer.addLast(data);
        return Collections.emptyList();
      case HEALTHY:
        return applyIncrement(data);
      case NEEDS_REBUILD:
        // Deltas of a broken chain are untrusted; the fresh snapshot rebuilds.
        return Collections.emptyList();
      default:
        throw new IllegalStateException("Unknown assembler state " + state);
    }
  }

  private List<OrderBook> applySnapshot(CryptoComOrderBookData data) {
    if (state != State.WAITING_SNAPSHOT && state != State.NEEDS_REBUILD) {
      // A full snapshot while healthy is protocol-compliant (the server substitutes a snapshot
      // when an increment would be too large); replace the book wholesale.
      LOG.debug("Replacing {} book from full snapshot", channel);
    }
    List<LimitOrder> asks = toLimitOrders(data.getAsks(), OrderType.ASK);
    List<LimitOrder> bids = toLimitOrders(data.getBids(), OrderType.BID);
    book = trim(new OrderBook(toDate(data.getTimestamp()), asks, bids, true));
    lastAppliedSequence = data.getSequence();
    state = State.HEALTHY;
    List<OrderBook> emissions = new ArrayList<>();
    emissions.add(copyOf(book));

    // Flush increments buffered while this snapshot was being acquired, in arrival order,
    // enforcing the same sequence chain as for live increments.
    while (!snapshotBuffer.isEmpty()) {
      CryptoComOrderBookData buffered = snapshotBuffer.removeFirst();
      if (state == State.NEEDS_REBUILD) {
        // A buffered increment already broke the chain; drop the rest of the buffer.
        snapshotBuffer.clear();
        break;
      }
      emissions.addAll(applyIncrement(buffered));
    }
    return emissions;
  }

  private List<OrderBook> applyIncrement(CryptoComOrderBookData data) {
    Long u = data.getSequence();
    if (u != null && lastAppliedSequence != null && u.compareTo(lastAppliedSequence) <= 0) {
      // Stale or duplicate delivery of an update already applied on this connection.
      LOG.debug(
          "Ignoring stale/duplicate {} update u={} (last applied u={})",
          channel, u, lastAppliedSequence);
      return Collections.emptyList();
    }
    Long pu = data.getPreviousSequence();
    if (u != null && lastAppliedSequence != null && !pu.equals(lastAppliedSequence)) {
      failSequence(lastAppliedSequence, u, pu);
      snapshotBuffer.clear();
      state = State.NEEDS_REBUILD;
      return Collections.emptyList();
    }
    if (lastAppliedSequence == null && pu != null) {
      // Degenerate server without u tracking: trust arrival order.
      LOG.debug(
          "{} sequence tracking unavailable (u={}, pu={}); trusting arrival order", channel, u, pu);
    }
    applyLevels(data.getAsks(), OrderType.ASK);
    applyLevels(data.getBids(), OrderType.BID);
    lastAppliedSequence = u;
    book = trim(book);
    return Collections.singletonList(copyOf(book));
  }

  /** Deep-copies the book so emitted/queried books are immune to later in-place updates. */
  private static OrderBook copyOf(OrderBook source) {
    return new OrderBook(
        source.getTimeStamp(),
        new ArrayList<>(source.getAsks()),
        new ArrayList<>(source.getBids()));
  }

  private void failSequence(Long lastApplied, Long u, Long pu) {
    CryptoComOrderBookContinuityException failure =
        new CryptoComOrderBookContinuityException(channel, lastApplied, u, pu);
    LOG.warn(failure.getMessage());
    failures.onNext(failure);
  }

  private void applyLevels(List<List<String>> levels, OrderType type) {
    if (levels == null) {
      return;
    }
    for (List<String> level : levels) {
      if (level == null || level.size() < 2) {
        continue;
      }
      BigDecimal price = new BigDecimal(level.get(0));
      BigDecimal quantity = new BigDecimal(level.get(1));
      book.update(new LimitOrder(type, quantity, currencyPair, null, null, price));
    }
  }

  /** Converts raw {@code [price, quantity(, numberOfOrders)]} rows to order-book levels. */
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private List<LimitOrder> toLimitOrders(List<List<String>> levels, OrderType type) {
    if (levels == null) {
      return Collections.emptyList();
    }
    List<LimitOrder> orders = new ArrayList<>(levels.size());
    for (List<String> level : levels) {
      if (level == null || level.size() < 2) {
        continue;
      }
      orders.add(
          new LimitOrder(
              type, new BigDecimal(level.get(1)), currencyPair, null, null, new BigDecimal(level.get(0))));
    }
    return orders;
  }

  /** Keeps only the best {@code depth} levels per side (asks ascending, bids descending). */
  private OrderBook trim(OrderBook toTrim) {
    List<LimitOrder> asks = toTrim.getAsks();
    if (asks.size() > depth) {
      asks.subList(depth, asks.size()).clear();
    }
    List<LimitOrder> bids = toTrim.getBids();
    if (bids.size() > depth) {
      bids.subList(depth, bids.size()).clear();
    }
    return toTrim;
  }

  private static java.util.Date toDate(Long epochMillis) {
    return epochMillis == null ? null : new java.util.Date(epochMillis);
  }

  private enum State {
    WAITING_SNAPSHOT,
    HEALTHY,
    NEEDS_REBUILD
  }
}