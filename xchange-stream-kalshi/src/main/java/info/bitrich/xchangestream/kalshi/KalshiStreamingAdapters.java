package info.bitrich.xchangestream.kalshi;

import info.bitrich.xchangestream.kalshi.dto.KalshiWsFill;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsTicker;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsTrade;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsUserOrder;
import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kalshi.KalshiAdapters;

/**
 * Conversions between Kalshi WebSocket message payloads and generic XChange DTOs.
 *
 * <p>The WebSocket surface reports prices as dollar strings, so the integer-cents conversions of
 * the REST adapters do not apply here; the side rules do, unchanged:
 *
 * <ul>
 *   <li>Trades follow the REST {@code adaptTrades} rule: a {@code no} taker side reads as an
 *       ask-side aggressor on the YES leg.
 *   <li>Fills follow {@link KalshiAdapters#RULE_BOOK_SIDE_DIRECTION}: the legacy {@code action}/
 *       {@code side} pair collapses to the canonical {@code book_side} truth table ({@code buy
 *       yes}/{@code sell no} read as BID, {@code sell yes}/{@code buy no} read as ASK), always
 *       priced at {@code yes_price_dollars} (the YES complement of a NO-side price).
 *   <li>User orders follow {@link KalshiAdapters#RULE_YES_LEG_ONLY}: the documented {@code
 *       book_side} is the YES-book side the order rests on, so {@code bid} maps to generic BID and
 *       {@code ask} to generic ASK at {@code yes_price_dollars}. An unrecognized {@code
 *       book_side} is rejected, never guessed.
 * </ul>
 */
public final class KalshiStreamingAdapters {

  private KalshiStreamingAdapters() {}

  /** Adapts a ticker message to a generic YES-leg ticker. */
  public static Ticker adaptTicker(KalshiWsTicker ticker) {
    return new Ticker.Builder()
        .instrument(KalshiAdapters.contractForTicker(ticker.marketTicker()))
        .bid(decimal(ticker.yesBidDollars()))
        .ask(decimal(ticker.yesAskDollars()))
        .last(decimal(ticker.priceDollars()))
        .volume(decimal(ticker.volumeFp()))
        .timestamp(date(ticker.tsMs()))
        .build();
  }

  /** Adapts a public trade; a {@code no} taker side reads as an ask-side aggressor on YES. */
  public static Trade adaptTrade(KalshiWsTrade trade) {
    return Trade.builder()
        .type("no".equalsIgnoreCase(trade.takerSide()) ? OrderType.ASK : OrderType.BID)
        .originalAmount(decimal(trade.countFp()))
        .instrument(KalshiAdapters.contractForTicker(trade.marketTicker()))
        .price(decimal(trade.yesPriceDollars()))
        .timestamp(date(trade.tsMs()))
        .id(trade.tradeId())
        .build();
  }

  /** Adapts a user fill per {@link KalshiAdapters#RULE_BOOK_SIDE_DIRECTION}. */
  public static UserTrade adaptFill(KalshiWsFill fill) {
    return UserTrade.builder()
        .type(genericType(fill.action(), fill.side()))
        .originalAmount(decimal(fill.countFp()))
        .instrument(KalshiAdapters.contractForTicker(fill.marketTicker()))
        .price(decimal(fill.yesPriceDollars()))
        .timestamp(date(fill.tsMs()))
        .id(fill.tradeId())
        .orderId(fill.orderId())
        .feeAmount(decimal(fill.feeCost()))
        .feeCurrency(fill.feeCost() == null ? null : Currency.USD)
        .build();
  }

  /** Adapts a user order update per {@link KalshiAdapters#RULE_YES_LEG_ONLY}. */
  public static LimitOrder adaptUserOrder(KalshiWsUserOrder order) {
    return new LimitOrder.Builder(
            genericTypeFromBookSide(order.bookSide()),
            KalshiAdapters.contractForTicker(order.ticker()))
        .originalAmount(decimal(order.initialCountFp()))
        .cumulativeAmount(decimal(order.fillCountFp()))
        .limitPrice(decimal(order.yesPriceDollars()))
        .id(order.orderId())
        .userReference(order.clientOrderId())
        .timestamp(date(order.createdTsMs()))
        .orderStatus(adaptOrderStatus(order))
        .build();
  }

  /**
   * Maps the lifecycle status to the generic order status, mirroring the REST {@code
   * adaptOrderStatus} truth table: resting with fills is partially filled.
   */
  static OrderStatus adaptOrderStatus(KalshiWsUserOrder order) {
    String status = order.status() == null ? "" : order.status();
    return switch (status) {
      case "resting" ->
          decimal(order.fillCountFp()) != null
                  && decimal(order.fillCountFp()).compareTo(BigDecimal.ZERO) > 0
              ? OrderStatus.PARTIALLY_FILLED
              : OrderStatus.OPEN;
      case "canceled" -> OrderStatus.CANCELED;
      case "executed" -> OrderStatus.FILLED;
      default -> OrderStatus.UNKNOWN;
    };
  }

  private static OrderType genericType(String action, String side) {
    boolean buy = "buy".equalsIgnoreCase(action);
    boolean yes = !"no".equalsIgnoreCase(side);
    // RULE_BOOK_SIDE_DIRECTION: buy+no -> ASK YES, sell+no -> BID YES.
    return buy == yes ? OrderType.BID : OrderType.ASK;
  }

  private static OrderType genericTypeFromBookSide(String bookSide) {
    if ("bid".equalsIgnoreCase(bookSide)) {
      return OrderType.BID;
    }
    if ("ask".equalsIgnoreCase(bookSide)) {
      return OrderType.ASK;
    }
    throw new ExchangeException("Kalshi user order has unrecognized book_side: " + bookSide);
  }

  private static BigDecimal decimal(String fixedPoint) {
    return fixedPoint == null || fixedPoint.isBlank() ? null : new BigDecimal(fixedPoint);
  }

  private static Date date(Long epochMillis) {
    return epochMillis == null ? null : new Date(epochMillis);
  }
}
