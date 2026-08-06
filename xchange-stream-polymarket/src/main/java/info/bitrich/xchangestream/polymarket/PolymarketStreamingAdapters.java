package info.bitrich.xchangestream.polymarket;

import info.bitrich.xchangestream.polymarket.dto.PolymarketWsBook;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsLastTradePrice;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsOrder;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsPriceChange;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsTrade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.polymarket.PolymarketAdapters;

/**
 * Conversions between Polymarket CLOB WebSocket payloads and generic XChange DTOs.
 *
 * <p>The WebSocket surface reports prices in dollars per share, exactly like the CLOB REST API, so
 * the REST side rules apply unchanged:
 *
 * <ul>
 *   <li>Sides follow {@link PolymarketAdapters#RULE_TOKEN_DIRECT}: {@code BUY} on the outcome
 *       token reads as generic BID, {@code SELL} as ASK, priced in dollars per share.
 *   <li>Instruments follow {@link PolymarketAdapters#RULE_NO_COMPLEMENT}: every event adapts to
 *       the contract of the token it actually references ({@code market} + {@code asset_id});
 *       outcome tokens are never complemented.
 *   <li>Order status follows the REST {@code adaptOrderStatus} truth table (live with fills reads
 *       as partially filled), with the WebSocket's uppercase status spellings.
 * </ul>
 */
public final class PolymarketStreamingAdapters {

  private PolymarketStreamingAdapters() {}

  /**
   * Adapts a full book event to generic depth; levels arrive worst-first and are re-sorted, same
   * as the REST {@code adaptOrderBook}.
   */
  public static OrderBook adaptBook(PolymarketWsBook book) {
    List<LimitOrder> bids = new ArrayList<>();
    List<LimitOrder> asks = new ArrayList<>();
    if (book.bids() != null) {
      for (PolymarketWsBook.Level level : book.bids()) {
        bids.add(level(book, OrderType.BID, level));
      }
    }
    if (book.asks() != null) {
      for (PolymarketWsBook.Level level : book.asks()) {
        asks.add(level(book, OrderType.ASK, level));
      }
    }
    bids.sort(Comparator.comparing(LimitOrder::getLimitPrice).reversed());
    asks.sort(Comparator.comparing(LimitOrder::getLimitPrice));
    return new OrderBook(epochMillis(book.timestamp()), asks, bids);
  }

  /** Adapts a full book event to a top-of-book ticker. */
  public static Ticker adaptTicker(PolymarketWsBook book) {
    OrderBook orderBook = adaptBook(book);
    Ticker.Builder builder =
        new Ticker.Builder()
            .instrument(PolymarketAdapters.contractForToken(book.market(), book.assetId()))
            .timestamp(orderBook.getTimeStamp());
    if (!orderBook.getBids().isEmpty()) {
      builder.bid(orderBook.getBids().get(0).getLimitPrice());
    }
    if (!orderBook.getAsks().isEmpty()) {
      builder.ask(orderBook.getAsks().get(0).getLimitPrice());
    }
    return builder.build();
  }

  /** Adapts one price-change entry to a top-of-book ticker from its best bid/ask fields. */
  public static Ticker adaptTicker(PolymarketWsPriceChange.Change change, String market, String timestamp) {
    return new Ticker.Builder()
        .instrument(PolymarketAdapters.contractForToken(market, change.assetId()))
        .bid(decimal(change.bestBid()))
        .ask(decimal(change.bestAsk()))
        .timestamp(epochMillis(timestamp))
        .build();
  }

  /** Adapts a last-trade event; a {@code SELL} aggressor reads as ask-side, mirroring REST trades. */
  public static Trade adaptLastTradePrice(PolymarketWsLastTradePrice trade) {
    return Trade.builder()
        .type("SELL".equalsIgnoreCase(trade.side()) ? OrderType.ASK : OrderType.BID)
        .originalAmount(decimal(trade.size()))
        .instrument(PolymarketAdapters.contractForToken(trade.market(), trade.assetId()))
        .price(decimal(trade.price()))
        .timestamp(epochMillis(trade.timestamp()))
        .id(trade.transactionHash())
        .build();
  }

  /**
   * Adapts a user order event per {@link PolymarketAdapters#RULE_TOKEN_DIRECT}. Placement,
   * update, and cancellation events all carry the full current state, so they adapt uniformly.
   */
  public static LimitOrder adaptOrder(PolymarketWsOrder order) {
    LimitOrder.Builder builder =
        new LimitOrder.Builder(
                "SELL".equalsIgnoreCase(order.side()) ? OrderType.ASK : OrderType.BID,
                PolymarketAdapters.contractForToken(order.market(), order.assetId()))
            .originalAmount(decimal(order.originalSize()))
            .limitPrice(decimal(order.price()))
            .id(order.id())
            .timestamp(epochSeconds(order.createdAt()))
            .orderStatus(adaptOrderStatus(order.status(), order.sizeMatched()));
    BigDecimal matched = decimal(order.sizeMatched());
    if (matched != null && matched.compareTo(BigDecimal.ZERO) > 0) {
      builder.cumulativeAmount(matched);
    }
    return builder.build();
  }

  /**
   * Maps the wire status to the generic order status, mirroring the REST {@code adaptOrderStatus}
   * truth table with the WebSocket's uppercase spellings: live with fills is partially filled.
   */
  static OrderStatus adaptOrderStatus(String status, String sizeMatched) {
    String value = status == null ? "" : status.toUpperCase();
    return switch (value) {
      case "LIVE" ->
          decimal(sizeMatched) != null && decimal(sizeMatched).compareTo(BigDecimal.ZERO) > 0
              ? OrderStatus.PARTIALLY_FILLED
              : OrderStatus.OPEN;
      case "MATCHED" -> OrderStatus.FILLED;
      case "CANCELED" -> OrderStatus.CANCELED;
      case "DELAYED" -> OrderStatus.PENDING_NEW;
      default -> OrderStatus.UNKNOWN;
    };
  }

  /**
   * Adapts a user trade event to one fill per user order involved: a {@code TAKER} event is the
   * single taker fill, a {@code MAKER} event yields one fill per matched maker order. An
   * unrecognized {@code trader_side} is rejected, never guessed.
   *
   * @param trade user trade event
   * @return one entry per fill on the user's own orders
   */
  public static List<UserTrade> adaptUserTrades(PolymarketWsTrade trade) {
    List<UserTrade> fills = new ArrayList<>();
    String traderSide = trade.traderSide() == null ? "" : trade.traderSide().toUpperCase();
    switch (traderSide) {
      case "TAKER" ->
          fills.add(
              userTrade(
                  trade,
                  trade.takerOrderId(),
                  trade.side(),
                  trade.size(),
                  trade.price()));
      case "MAKER" -> {
        if (trade.makerOrders() != null) {
          for (PolymarketWsTrade.MakerOrder maker : trade.makerOrders()) {
            fills.add(
                userTrade(
                    trade, maker.orderId(), maker.side(), maker.matchedAmount(), maker.price()));
          }
        }
      }
      default ->
          throw new ExchangeException(
              "Polymarket user trade has unrecognized trader_side: " + trade.traderSide());
    }
    return fills;
  }

  private static UserTrade userTrade(
      PolymarketWsTrade trade, String orderId, String side, String size, String price) {
    return UserTrade.builder()
        .type("SELL".equalsIgnoreCase(side) ? OrderType.ASK : OrderType.BID)
        .originalAmount(decimal(size))
        .instrument(PolymarketAdapters.contractForToken(trade.market(), trade.assetId()))
        .price(decimal(price))
        .timestamp(epochSeconds(trade.matchTime()))
        .id(trade.id())
        .orderId(orderId)
        .build();
  }

  private static LimitOrder level(
      PolymarketWsBook book, OrderType type, PolymarketWsBook.Level level) {
    return new LimitOrder.Builder(
            type, PolymarketAdapters.contractForToken(book.market(), book.assetId()))
        .originalAmount(decimal(level.size()))
        .limitPrice(decimal(level.price()))
        .build();
  }

  private static BigDecimal decimal(String value) {
    return value == null || value.isBlank() ? null : new BigDecimal(value);
  }

  private static Date epochMillis(String value) {
    return value == null || value.isBlank() ? null : new Date(Long.parseLong(value));
  }

  private static Date epochSeconds(String value) {
    return value == null || value.isBlank() ? null : new Date(Long.parseLong(value) * 1000L);
  }
}
