package info.bitrich.xchangestream.polymarket;

import info.bitrich.xchangestream.polymarket.dto.PolymarketWsBook;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsPriceChange;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Client-side Polymarket order book for one outcome token: a {@code book} event anchors the full
 * level set, then {@code price_change} events carry the <em>absolute</em> new size of each touched
 * level (a zero size removes the level). Re-applying a change is therefore idempotent and a fresh
 * {@code book} event always re-anchors the state, which is the documented recovery path after a
 * reconnect.
 *
 * <p>The stream has no sequence numbers, so integrity follows the PRD rule that uncertainty must
 * surface, never silently continue: a price change arriving before any snapshot raises {@link
 * ExchangeException} with REST-resync guidance, as does a change naming an unexpected asset id or
 * an unrecognized side.
 *
 * <p>Levels are kept in dollars per share for the token actually referenced, per {@link
 * PolymarketAdapters#RULE_TOKEN_DIRECT} and {@link PolymarketAdapters#RULE_NO_COMPLEMENT}.
 */
final class PolymarketStreamingOrderBook {

  private final PredictionMarketContract contract;
  private final String assetId;
  private final TreeMap<BigDecimal, BigDecimal> bids = new TreeMap<>();
  private final TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
  private boolean snapshotSeen;
  private String timestamp;

  PolymarketStreamingOrderBook(String conditionId, String assetId) {
    this.contract = PolymarketAdapters.contractForToken(conditionId, assetId);
    this.assetId = assetId;
  }

  /** Replaces all state with the snapshot levels. */
  OrderBook applySnapshot(PolymarketWsBook snapshot) {
    bids.clear();
    asks.clear();
    putAll(bids, snapshot.bids());
    putAll(asks, snapshot.asks());
    snapshotSeen = true;
    timestamp = snapshot.timestamp();
    return toOrderBook();
  }

  /**
   * Applies one absolute level update; any pre-snapshot change, unexpected asset, or unrecognized
   * side raises {@link ExchangeException}.
   */
  OrderBook applyPriceChange(PolymarketWsPriceChange.Change change, String eventTimestamp) {
    if (!snapshotSeen) {
      throw new ExchangeException(
          "Polymarket price_change for "
              + change.assetId()
              + " arrived before any book snapshot; resync over REST before continuing");
    }
    if (!assetId.equals(change.assetId())) {
      throw new ExchangeException(
          "Polymarket price_change for unexpected asset "
              + change.assetId()
              + " on the channel for asset "
              + assetId);
    }
    TreeMap<BigDecimal, BigDecimal> levels;
    String side = change.side() == null ? "" : change.side().toUpperCase();
    switch (side) {
      case "BUY" -> levels = bids;
      case "SELL" -> levels = asks;
      default ->
          throw new ExchangeException(
              "Polymarket price_change has unrecognized side: " + change.side());
    }
    BigDecimal size = new BigDecimal(change.size());
    if (size.signum() <= 0) {
      levels.remove(new BigDecimal(change.price()));
    } else {
      levels.put(new BigDecimal(change.price()), size);
    }
    timestamp = eventTimestamp;
    return toOrderBook();
  }

  private OrderBook toOrderBook() {
    List<LimitOrder> bidOrders = new ArrayList<>();
    for (Map.Entry<BigDecimal, BigDecimal> level : bids.entrySet()) {
      bidOrders.add(limitOrder(OrderType.BID, level.getKey(), level.getValue()));
    }
    bidOrders.sort(Comparator.comparing(LimitOrder::getLimitPrice).reversed());
    List<LimitOrder> askOrders = new ArrayList<>();
    for (Map.Entry<BigDecimal, BigDecimal> level : asks.entrySet()) {
      askOrders.add(limitOrder(OrderType.ASK, level.getKey(), level.getValue()));
    }
    askOrders.sort(Comparator.comparing(LimitOrder::getLimitPrice));
    Date time = timestamp == null || timestamp.isBlank() ? null : new Date(Long.parseLong(timestamp));
    return new OrderBook(time, askOrders, bidOrders);
  }

  private LimitOrder limitOrder(OrderType type, BigDecimal price, BigDecimal size) {
    return new LimitOrder.Builder(type, contract)
        .originalAmount(size)
        .limitPrice(price)
        .build();
  }

  private static void putAll(
      TreeMap<BigDecimal, BigDecimal> levels, List<PolymarketWsBook.Level> snapshotLevels) {
    if (snapshotLevels == null) {
      return;
    }
    for (PolymarketWsBook.Level level : snapshotLevels) {
      levels.put(new BigDecimal(level.price()), new BigDecimal(level.size()));
    }
  }
}
