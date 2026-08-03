package info.bitrich.xchangestream.kalshi;

import info.bitrich.xchangestream.kalshi.dto.KalshiWsOrderBookDelta;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsOrderBookSnapshot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kalshi.KalshiAdapters;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Client-side Kalshi order book fed by the {@code orderbook_delta} channel: one {@code
 * orderbook_snapshot} anchors the state, then {@code orderbook_delta} messages apply in strict
 * {@code seq} order.
 *
 * <p>Sequence integrity follows the PRD rule that gaps must surface, never silently continue: a
 * delta whose {@code seq} is not exactly the expected next sequence raises {@link
 * ExchangeException} so the caller can resync over REST, as does a delta arriving before any
 * snapshot. A fresh snapshot always re-anchors the state (the server's documented recovery path).
 *
 * <p>The YES-side levels are generic bids; NO-side levels are generic asks at the complement price
 * {@code 1 - noPrice}, the dollar form of {@link KalshiAdapters#RULE_NO_BID_COMPLEMENT}.
 */
final class KalshiStreamingOrderBook {

  private final PredictionMarketContract contract;
  private final TreeMap<BigDecimal, BigDecimal> yesLevels = new TreeMap<>();
  private final TreeMap<BigDecimal, BigDecimal> noLevels = new TreeMap<>();
  private long expectedSeq;
  private boolean snapshotSeen;

  KalshiStreamingOrderBook(String marketTicker) {
    this.contract = KalshiAdapters.contractForTicker(marketTicker);
  }

  /** Replaces all state with the snapshot levels and re-anchors the sequence. */
  OrderBook applySnapshot(long seq, KalshiWsOrderBookSnapshot snapshot) {
    yesLevels.clear();
    noLevels.clear();
    putAll(yesLevels, snapshot.yesDollarsFp());
    putAll(noLevels, snapshot.noDollarsFp());
    expectedSeq = seq + 1;
    snapshotSeen = true;
    return toOrderBook();
  }

  /** Applies one delta; any sequence gap or pre-snapshot delta raises {@link ExchangeException}. */
  OrderBook applyDelta(long seq, KalshiWsOrderBookDelta delta) {
    if (!snapshotSeen) {
      throw new ExchangeException(
          "Kalshi orderbook delta for "
              + delta.marketTicker()
              + " arrived before any snapshot; resync over REST before continuing");
    }
    if (seq != expectedSeq) {
      throw new ExchangeException(
          "Kalshi orderbook sequence gap for "
              + delta.marketTicker()
              + ": expected seq "
              + expectedSeq
              + " but received "
              + seq
              + "; resync over REST before continuing");
    }
    expectedSeq = seq + 1;
    TreeMap<BigDecimal, BigDecimal> levels =
        "no".equalsIgnoreCase(delta.side()) ? noLevels : yesLevels;
    BigDecimal price = new BigDecimal(delta.priceDollars());
    BigDecimal count = levels.getOrDefault(price, BigDecimal.ZERO).add(new BigDecimal(delta.deltaFp()));
    if (count.signum() <= 0) {
      levels.remove(price);
    } else {
      levels.put(price, count);
    }
    return toOrderBook();
  }

  private OrderBook toOrderBook() {
    List<LimitOrder> bids = new ArrayList<>();
    for (Map.Entry<BigDecimal, BigDecimal> level : yesLevels.entrySet()) {
      bids.add(limitOrder(OrderType.BID, level.getKey(), level.getValue()));
    }
    bids.sort(Comparator.comparing(LimitOrder::getLimitPrice).reversed());
    List<LimitOrder> asks = new ArrayList<>();
    for (Map.Entry<BigDecimal, BigDecimal> level : noLevels.entrySet()) {
      // RULE_NO_BID_COMPLEMENT (dollar form): a NO bid at p is a YES ask at 1 - p.
      asks.add(limitOrder(OrderType.ASK, BigDecimal.ONE.subtract(level.getKey()), level.getValue()));
    }
    asks.sort(Comparator.comparing(LimitOrder::getLimitPrice));
    return new OrderBook(null, asks, bids);
  }

  private LimitOrder limitOrder(OrderType type, BigDecimal price, BigDecimal count) {
    return new LimitOrder.Builder(type, contract)
        .originalAmount(count)
        .limitPrice(price)
        .build();
  }

  private static void putAll(
      TreeMap<BigDecimal, BigDecimal> levels, List<List<String>> dollarLevels) {
    if (dollarLevels == null) {
      return;
    }
    for (List<String> level : dollarLevels) {
      levels.put(new BigDecimal(level.get(0)), new BigDecimal(level.get(1)));
    }
  }
}
