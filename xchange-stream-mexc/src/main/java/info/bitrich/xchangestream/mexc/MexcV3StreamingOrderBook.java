package info.bitrich.xchangestream.mexc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PublicAggreDepthV3ApiItem;
import com.mxc.push.common.protobuf.PublicAggreDepthsV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Depth;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3PriceLevel;
import org.knowm.xchange.mexc.v3.service.MexcV3MarketDataServiceRaw;

/**
 * Incremental order book for the {@code spot@public.aggre.depth.v3.api.pb} channel.
 *
 * <p>MEXC pushes only price-level deltas: each push carries a {@code fromVersion}/{@code
 * toVersion} version window and the levels changed inside it (a level with {@code quantity == 0}
 * is removed). A full snapshot is never pushed, so the book is reconciled against {@code GET
 * /api/v3/depth}:
 *
 * <ul>
 *   <li>First push (or a version gap, {@code fromVersion > lastUpdateId + 1}) triggers a REST
 *       snapshot fetch on the IO scheduler; the snapshot's {@code lastUpdateId} becomes the local
 *       reference point. A push whose version window contains that snapshot is applied immediately.
 *       A snapshot older than the current local reference is ignored so a delayed REST response
 *       cannot rewind sequence state.
 *   <li>After a re-snapshot, a push that starts after the snapshot is dropped; the next push that
 *       continues from the snapshot's version is applied.
 *   <li>Pushes whose {@code toVersion} is at or below the local {@code lastUpdateId} are stale and
 *       dropped.
 * </ul>
 *
 * <p>When a push overlaps a snapshot, its {@code fromVersion} may be below the snapshot version;
 * it is valid as long as its {@code toVersion} reaches the snapshot version.
 *
 * <p>This instance is not thread-safe; subscribe one {@link #onDelta(String)} stream per book.
 */
final class MexcV3StreamingOrderBook {

  /** MEXC depth snapshot depth; matches what the delta stream can fully cover between re-fetches. */
  private static final int SNAPSHOT_LIMIT = 5000;

  private final CurrencyPair currencyPair;
  private final MexcV3MarketDataServiceRaw rawMarketDataService;
  private final NavigableMap<BigDecimal, BigDecimal> bids =
      new TreeMap<>(Comparator.reverseOrder());
  private final NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>();

  private boolean initialized;
  private long lastUpdateId;

  MexcV3StreamingOrderBook(
      CurrencyPair currencyPair, MexcV3MarketDataServiceRaw rawMarketDataService) {
    this.currencyPair = currencyPair;
    this.rawMarketDataService = rawMarketDataService;
  }

  /**
   * Applies one {@code aggre.depth} push and emits the resulting book.
   *
   * <p>Emits empty when the push is stale or was dropped after a re-snapshot; errors on a body
   * that is not {@code publicAggreDepths} or unparseable version/level values.
   */
  Observable<OrderBook> onDelta(String canonicalJson) {
    PushDataV3ApiWrapper wrapper;
    try {
      wrapper = MexcV3StreamingAdapters.parsePush(canonicalJson);
      if (wrapper.getBodyCase() != PushDataV3ApiWrapper.BodyCase.PUBLICAGGREDEPTHS) {
        return Observable.error(
            new IllegalArgumentException(
                "MEXC v3 aggre.depth channel push carried an unexpected body: "
                    + wrapper.getBodyCase()));
      }
      PublicAggreDepthsV3Api delta = wrapper.getPublicAggreDepths();
      long fromVersion = Long.parseLong(delta.getFromVersion());
      long toVersion = Long.parseLong(delta.getToVersion());
      if (initialized && toVersion <= lastUpdateId) {
        return Observable.empty(); // stale push; the local book is already ahead of it
      }
      if (!initialized || !coversNextVersion(fromVersion, toVersion, lastUpdateId)) {
        boolean wasInitialized = initialized;
        long previousLastUpdateId = lastUpdateId;
        return refetchSnapshot()
            .concatMap(
                snapshot -> {
                  if (wasInitialized && snapshot.getLastUpdateId() < previousLastUpdateId) {
                    return Observable.empty(); // never rewind a book to a stale REST snapshot
                  }
                  initialize(snapshot);
                  if (!coversNextVersion(fromVersion, toVersion, lastUpdateId)) {
                    return Observable.empty(); // snapshot is outside this push's version window
                  }
                  applyDelta(delta);
                  lastUpdateId = toVersion;
                  return Observable.just(book(wrapper));
                });
      }
      applyDelta(delta);
      lastUpdateId = toVersion;
      return Observable.just(book(wrapper));
    } catch (InvalidProtocolBufferException | RuntimeException e) {
      return Observable.error(e);
    }
  }

  private static boolean coversNextVersion(long fromVersion, long toVersion, long lastUpdateId) {
    long nextVersion = lastUpdateId + 1;
    return fromVersion <= nextVersion && nextVersion <= toVersion;
  }

  private Observable<MexcV3Depth> refetchSnapshot() {
    return Observable.fromCallable(
            () -> {
              try {
                return rawMarketDataService.getDepth(currencyPair, SNAPSHOT_LIMIT);
              } catch (MexcV3Exception e) {
                throw e.adapt();
              }
            })
        .subscribeOn(Schedulers.io());
  }

  private void initialize(MexcV3Depth snapshot) {
    bids.clear();
    asks.clear();
    for (MexcV3PriceLevel level : snapshot.getBids()) {
      bids.put(new BigDecimal(level.getPrice()), new BigDecimal(level.getQuantity()));
    }
    for (MexcV3PriceLevel level : snapshot.getAsks()) {
      asks.put(new BigDecimal(level.getPrice()), new BigDecimal(level.getQuantity()));
    }
    lastUpdateId = snapshot.getLastUpdateId();
    initialized = true;
  }

  private void applyDelta(PublicAggreDepthsV3Api delta) {
    for (PublicAggreDepthV3ApiItem item : delta.getAsksList()) {
      applyLevel(asks, item);
    }
    for (PublicAggreDepthV3ApiItem item : delta.getBidsList()) {
      applyLevel(bids, item);
    }
  }

  private static void applyLevel(
      NavigableMap<BigDecimal, BigDecimal> levels, PublicAggreDepthV3ApiItem item) {
    BigDecimal price = new BigDecimal(item.getPrice());
    BigDecimal quantity = new BigDecimal(item.getQuantity());
    if (quantity.compareTo(BigDecimal.ZERO) == 0) {
      levels.remove(price);
    } else {
      levels.put(price, quantity);
    }
  }

  private OrderBook book(PushDataV3ApiWrapper wrapper) {
    Date timestamp = new Date(wrapper.getCreateTime());
    return new OrderBook(
        timestamp, toLimitOrders(asks, OrderType.ASK, timestamp), toLimitOrders(bids, OrderType.BID, timestamp), true);
  }

  private List<LimitOrder> toLimitOrders(
      NavigableMap<BigDecimal, BigDecimal> levels, OrderType type, Date timestamp) {
    List<LimitOrder> orders = new ArrayList<>(levels.size());
    for (NavigableMap.Entry<BigDecimal, BigDecimal> level : levels.entrySet()) {
      orders.add(
          new LimitOrder.Builder(type, currencyPair)
              .timestamp(timestamp)
              .limitPrice(level.getKey())
              .originalAmount(level.getValue())
              .build());
    }
    return orders;
  }
}
