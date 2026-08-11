package info.bitrich.xchangestream.kucoin;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;

/**
 * Sequence-safe UTA order-book assembler implementing the documented calibration procedure for
 * {@code obu} with {@code depth=increment@10ms} (snapshot first, then deltas).
 *
 * <p>Continuity rule per the official docs: for each delta, {@code sequenceStart(new) &lt;=
 * sequenceEnd(old) + 1} and {@code sequenceEnd(new) &gt; sequenceEnd(old)}; deltas with sequence
 * numbers not exceeding the snapshot's sequence are dropped; a size of zero removes the price
 * level; every other delta sets the absolute size. A continuity violation is reported as a gap and
 * the assembler clears state so the caller must rebuild from a fresh authoritative snapshot — no
 * path continues on unproved sequence state.
 *
 * @see <a href="https://www.kucoin.com/docs-new/3470221w0">Orderbook channel documentation</a>
 */
public final class UtaOrderBookAssembler {

  public enum Result {
    /** Update applied; book advanced to the update's end sequence. */
    APPLIED,
    /** Update was stale or duplicate; dropped silently (relaxed overlap is documented). */
    STALE_DROPPED,
    /** Update arrived before any snapshot; dropped. */
    AWAITING_SNAPSHOT,
    /** Sequence continuity violated; state cleared, caller must rebuild from a fresh snapshot. */
    GAP
  }

  private final TreeMap<BigDecimal, BigDecimal> bids =
      new TreeMap<>(Comparator.reverseOrder());
  private final TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
  private long lastSequence = -1L;
  private boolean synced;

  /**
   * Applies a snapshot or delta update.
   *
   * @param snapshot true when the push carries a full snapshot ({@code t == "snapshot"}), in which
   *     case {@code sequenceStart == sequenceEnd}
   * @param sequenceStart the O field
   * @param sequenceEnd the C field
   * @param bidLevels {@code [price, size]} pairs, or {@code null}
   * @param askLevels {@code [price, size]} pairs, or {@code null}
   */
  public Result onUpdate(
      boolean snapshot,
      long sequenceStart,
      long sequenceEnd,
      List<List<BigDecimal>> bidLevels,
      List<List<BigDecimal>> askLevels) {
    if (snapshot) {
      bids.clear();
      asks.clear();
      apply(bids, bidLevels);
      apply(asks, askLevels);
      lastSequence = sequenceEnd;
      synced = true;
      return Result.APPLIED;
    }
    if (!synced) {
      return Result.AWAITING_SNAPSHOT;
    }
    if (sequenceEnd <= lastSequence) {
      return Result.STALE_DROPPED;
    }
    if (sequenceStart > lastSequence + 1) {
      reset();
      return Result.GAP;
    }
    apply(bids, bidLevels);
    apply(asks, askLevels);
    lastSequence = sequenceEnd;
    return Result.APPLIED;
  }

  private static void apply(TreeMap<BigDecimal, BigDecimal> book, List<List<BigDecimal>> levels) {
    if (levels == null) {
      return;
    }
    for (List<BigDecimal> level : levels) {
      if (level == null || level.size() < 2) {
        continue;
      }
      BigDecimal price = level.get(0);
      BigDecimal size = level.get(1);
      if (size == null || size.signum() == 0) {
        book.remove(price);
      } else {
        book.put(price, size);
      }
    }
  }

  /** Clears the assembled state; the next accepted update must be a snapshot. */
  public void reset() {
    bids.clear();
    asks.clear();
    lastSequence = -1L;
    synced = false;
  }

  public boolean isSynced() {
    return synced;
  }

  public long getLastSequence() {
    return lastSequence;
  }

  public OrderBook toOrderBook(Instrument instrument, Date timestamp) {
    List<LimitOrder> askOrders = new LinkedList<>();
    for (var entry : asks.entrySet()) {
      askOrders.add(
          new LimitOrder.Builder(OrderType.ASK, instrument)
              .limitPrice(entry.getKey())
              .originalAmount(entry.getValue())
              .build());
    }
    List<LimitOrder> bidOrders = new LinkedList<>();
    for (var entry : bids.entrySet()) {
      bidOrders.add(
          new LimitOrder.Builder(OrderType.BID, instrument)
              .limitPrice(entry.getKey())
              .originalAmount(entry.getValue())
              .build());
    }
    return new OrderBook(timestamp, askOrders, bidOrders, true);
  }
}
