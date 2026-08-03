package org.knowm.xchange.kalshi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kalshi.dto.account.KalshiBalanceResponse;
import org.knowm.xchange.kalshi.dto.account.KalshiPositionsResponse.KalshiMarketPosition;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiTradesResponse.KalshiTradeRecord;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse.KalshiFill;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrder;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderFlags;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderRequest;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Conversions between Kalshi wire DTOs and generic XChange DTOs.
 *
 * <p>Named provider rules (each enforced by an adapter test):
 *
 * <ul>
 *   <li>{@link #RULE_YES_LEG_ONLY} — generic {@code BID} maps to native {@code bid} (buy YES),
 *       generic {@code ASK} maps to native {@code ask} (sell YES).
 *   <li>{@link #RULE_NO_BID_COMPLEMENT} — on the order-book read surface, Kalshi NO bids are YES
 *       asks at the complement price {@code (100 - noBidCents) / 100} dollars.
 *   <li>{@link #RULE_LEGACY_NO_COMPLEMENT} — on the legacy order/fill read surface, a
 *       {@code buy NO} at price {@code q} is economically a {@code sell YES} (ASK) at
 *       {@code 1 - q}, and a {@code sell NO} is a {@code buy YES} (BID) at {@code 1 - q}.
 *   <li>{@link #RULE_SIDE_NO_REJECTED} — an explicit {@link KalshiOrderFlags#SIDE_NO} flag on
 *       order placement is rejected; NO exposure is never silently synthesized.
 * </ul>
 */
public final class KalshiAdapters {

  /** Prediction-market provider id used in every Kalshi {@link PredictionMarketContract}. */
  public static final String PROVIDER = "kalshi";

  /** Outcome id carried by generic Kalshi instruments: the YES leg. */
  public static final String OUTCOME_YES = "YES";

  /** Named provider rule: placement-side mapping. */
  public static final String RULE_YES_LEG_ONLY =
      "Kalshi V2 event orders are YES-leg only: generic BID maps to native 'bid' (buy YES),"
          + " generic ASK maps to native 'ask' (sell YES).";

  /** Named provider rule: order-book NO-bid conversion. */
  public static final String RULE_NO_BID_COMPLEMENT =
      "Kalshi NO bids are YES asks at the complement price (100 - no_bid) / 100 dollars.";

  /** Named provider rule: legacy order/fill read-side conversion for NO-side records. */
  public static final String RULE_LEGACY_NO_COMPLEMENT =
      "Kalshi legacy 'buy NO' at price q reads as ASK YES at (1 - q); 'sell NO' reads as BID YES"
          + " at (1 - q).";

  /** Named provider rule: explicit NO-leg placement is rejected, never complemented. */
  public static final String RULE_SIDE_NO_REJECTED =
      "Kalshi SIDE_NO order flag is rejected: NO-leg placement is not available through the"
          + " generic API and is never silently complemented into a YES order.";

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private KalshiAdapters() {}

  /**
   * Builds the generic contract for a market record, keeping the event id as identity segment.
   *
   * @param market market record
   * @return YES-leg prediction-market contract quoted in USD
   */
  public static PredictionMarketContract adaptContract(KalshiMarket market) {
    return new PredictionMarketContract(
        PROVIDER, market.eventTicker(), market.ticker(), OUTCOME_YES, Currency.USD);
  }

  /**
   * Builds the generic contract for a bare market ticker (no event segment known).
   *
   * @param ticker market ticker
   * @return YES-leg prediction-market contract quoted in USD
   */
  public static PredictionMarketContract contractForTicker(String ticker) {
    return new PredictionMarketContract(PROVIDER, null, ticker, OUTCOME_YES, Currency.USD);
  }

  /**
   * Extracts and validates the market ticker from a generic instrument.
   *
   * @param instrument generic instrument; must be a Kalshi {@link PredictionMarketContract}
   * @return the market ticker
   */
  public static String marketTicker(Instrument instrument) {
    if (!(instrument instanceof PredictionMarketContract contract)
        || !PROVIDER.equals(contract.getProvider())) {
      throw new InstrumentNotValidException(
          "Kalshi services require a PredictionMarketContract with provider 'kalshi': "
              + instrument);
    }
    return contract.getMarketId();
  }

  /** Converts integer cents to a dollar amount ({@code 53} → {@code 0.53}). */
  public static BigDecimal centsToDollars(Integer cents) {
    return cents == null ? null : BigDecimal.valueOf(cents.longValue(), 2);
  }

  /**
   * Converts a generic dollar limit price to the V2 fixed-point dollar string. The price must be
   * strictly between 0 and 1 dollars.
   */
  static String toKalshiPriceString(BigDecimal price) {
    if (price == null
        || price.compareTo(BigDecimal.ZERO) <= 0
        || price.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalArgumentException(
          "Kalshi limit price must be between 0 and 1 dollars exclusive: " + price);
    }
    return price.setScale(4, RoundingMode.HALF_UP).toPlainString();
  }

  /** Converts a generic contract count to the V2 fixed-point count string. */
  static String toKalshiCountString(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(
          "Kalshi contract count must be positive: " + amount);
    }
    return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  /** Adapts a market record to exchange metadata; prices step at one cent, one-contract lots. */
  public static InstrumentMetaData adaptMetadata(KalshiMarket market) {
    return InstrumentMetaData.builder()
        .priceScale(4)
        .volumeScale(2)
        .priceStepSize(new BigDecimal("0.01"))
        .amountStepSize(BigDecimal.ONE)
        .minimumAmount(BigDecimal.ONE)
        .contractValue(BigDecimal.ONE)
        .build();
  }

  /** Adapts a market record to a generic YES-leg ticker. */
  public static Ticker adaptTicker(KalshiMarket market) {
    return new Ticker.Builder()
        .instrument(adaptContract(market))
        .bid(centsToDollars(market.yesBid()))
        .ask(centsToDollars(market.yesAsk()))
        .last(centsToDollars(market.lastPrice()))
        .volume(market.volume() == null ? null : BigDecimal.valueOf(market.volume()))
        .build();
  }

  /**
   * Adapts the YES/NO order book to generic YES-leg depth, applying {@link
   * #RULE_NO_BID_COMPLEMENT}. Bids are sorted best-first, asks worst-last ordering is normalized
   * to best-first as well.
   */
  public static OrderBook adaptOrderBook(String ticker, KalshiOrderBookResponse response) {
    PredictionMarketContract contract = contractForTicker(ticker);
    List<LimitOrder> bids = new ArrayList<>();
    List<LimitOrder> asks = new ArrayList<>();
    if (response.orderbook() != null) {
      if (response.orderbook().yes() != null) {
        for (List<Integer> level : response.orderbook().yes()) {
          bids.add(level(contract, OrderType.BID, centsToDollars(level.get(0)), level.get(1)));
        }
      }
      if (response.orderbook().no() != null) {
        for (List<Integer> level : response.orderbook().no()) {
          asks.add(
              level(
                  contract,
                  OrderType.ASK,
                  centsToDollars(100 - level.get(0)),
                  level.get(1)));
        }
      }
    }
    bids.sort(Comparator.comparing(LimitOrder::getLimitPrice).reversed());
    asks.sort(Comparator.comparing(LimitOrder::getLimitPrice));
    return new OrderBook(null, asks, bids);
  }

  /** Adapts public trades; a {@code no} taker side reads as an ask-side aggressor on YES. */
  public static Trades adaptTrades(List<KalshiTradeRecord> trades) {
    List<Trade> adapted = new ArrayList<>();
    for (KalshiTradeRecord trade : trades) {
      adapted.add(
          Trade.builder()
              .type("no".equalsIgnoreCase(trade.takerSide()) ? OrderType.ASK : OrderType.BID)
              .originalAmount(
                  trade.count() == null ? null : BigDecimal.valueOf(trade.count()))
              .instrument(contractForTicker(trade.ticker()))
              .price(centsToDollars(trade.yesPrice()))
              .timestamp(parseTime(trade.createdTime()))
              .id(trade.tradeId())
              .build());
    }
    return new Trades(adapted);
  }

  /** Maps the generic order side to the native V2 side string per {@link #RULE_YES_LEG_ONLY}. */
  public static String toNativeSide(OrderType type) {
    return switch (type) {
      case BID -> "bid";
      case ASK -> "ask";
      default ->
          throw new NotAvailableFromExchangeException(
              "Kalshi supports limit BID/ASK only. " + RULE_YES_LEG_ONLY);
    };
  }

  /**
   * Builds the V2 create-order request for a generic limit order.
   *
   * <p>Applies {@link #RULE_YES_LEG_ONLY} for the side and {@link #RULE_SIDE_NO_REJECTED} for the
   * explicit NO-leg flag. Kalshi does not offer a verified idempotency guarantee beyond the
   * caller-supplied {@code client_order_id}; the generic user reference is passed through so
   * callers control retry identity.
   */
  public static KalshiOrderRequest toCreateOrderRequest(LimitOrder order) {
    String ticker = marketTicker(order.getInstrument());
    if (order.hasFlag(KalshiOrderFlags.SIDE_NO)) {
      throw new NotAvailableFromExchangeException(RULE_SIDE_NO_REJECTED);
    }
    String timeInForce = "good_till_canceled";
    if (order.hasFlag(KalshiOrderFlags.FILL_OR_KILL)) {
      timeInForce = "fill_or_kill";
    } else if (order.hasFlag(KalshiOrderFlags.IMMEDIATE_OR_CANCEL)) {
      timeInForce = "immediate_or_cancel";
    }
    return new KalshiOrderRequest(
        ticker,
        order.getUserReference(),
        toNativeSide(order.getType()),
        toKalshiPriceString(order.getLimitPrice()),
        toKalshiCountString(order.getOriginalAmount()),
        timeInForce,
        order.hasFlag(KalshiOrderFlags.POST_ONLY) ? Boolean.TRUE : null,
        order.hasFlag(KalshiOrderFlags.CANCEL_ON_PAUSE) ? Boolean.TRUE : null,
        order.hasFlag(KalshiOrderFlags.REDUCE_ONLY) ? Boolean.TRUE : null,
        "taker_at_cross");
  }

  /** Maps a legacy order record to a generic limit order per {@link #RULE_LEGACY_NO_COMPLEMENT}. */
  public static LimitOrder adaptOrder(KalshiOrder order) {
    OrderType type = genericType(order.action(), order.side());
    BigDecimal limitPrice = genericPrice(order.action(), order.side(), order.yesPrice(), order.noPrice());
    LimitOrder.Builder builder =
        new LimitOrder.Builder(type, contractForTicker(order.ticker()))
            .originalAmount(
                order.initialCount() == null ? null : BigDecimal.valueOf(order.initialCount()))
            .limitPrice(limitPrice)
            .id(order.orderId())
            .userReference(order.clientOrderId())
            .timestamp(parseTime(order.createdTime()))
            .orderStatus(adaptOrderStatus(order));
    if (order.fillCount() != null && order.fillCount() > 0) {
      builder.cumulativeAmount(BigDecimal.valueOf(order.fillCount()));
    }
    return builder.build();
  }

  /** Maps a legacy fill record to a generic user trade per {@link #RULE_LEGACY_NO_COMPLEMENT}. */
  public static UserTrade adaptFill(KalshiFill fill) {
    return UserTrade.builder()
        .type(genericType(fill.action(), fill.side()))
        .originalAmount(fill.count() == null ? null : BigDecimal.valueOf(fill.count()))
        .instrument(contractForTicker(fill.ticker()))
        .price(genericPrice(fill.action(), fill.side(), fill.yesPrice(), fill.noPrice()))
        .timestamp(parseTime(fill.createdTime()))
        .id(fill.fillId())
        .orderId(fill.orderId())
        .build();
  }

  /** Adapts the legacy lifecycle status to the generic order status. */
  public static org.knowm.xchange.dto.Order.OrderStatus adaptOrderStatus(KalshiOrder order) {
    String status = order.status() == null ? "" : order.status();
    return switch (status) {
      case "resting" ->
          order.fillCount() != null && order.fillCount() > 0
              ? org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED
              : org.knowm.xchange.dto.Order.OrderStatus.OPEN;
      case "canceled" -> org.knowm.xchange.dto.Order.OrderStatus.CANCELED;
      case "executed" -> org.knowm.xchange.dto.Order.OrderStatus.FILLED;
      case "pending" -> org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW;
      default -> org.knowm.xchange.dto.Order.OrderStatus.UNKNOWN;
    };
  }

  /** Adapts the portfolio balance to a single USD wallet; amounts are integer cents. */
  public static AccountInfo adaptAccountInfo(KalshiBalanceResponse balance) {
    BigDecimal available =
        balance.balance() == null ? BigDecimal.ZERO : BigDecimal.valueOf(balance.balance(), 2);
    Wallet wallet =
        new Wallet(null, null, List.of(new Balance(Currency.USD, available, available)), null, null, null);
    return new AccountInfo(wallet);
  }

  /** Adapts market positions; negative YES counts map to SHORT (net NO exposure). */
  public static List<OpenPosition> adaptPositions(List<KalshiMarketPosition> positions) {
    List<OpenPosition> adapted = new ArrayList<>();
    for (KalshiMarketPosition position : positions) {
      long size = position.position() == null ? 0L : position.position();
      adapted.add(
          OpenPosition.builder()
              .instrument(contractForTicker(position.ticker()))
              .type(size < 0 ? OpenPosition.Type.SHORT : OpenPosition.Type.LONG)
              .size(BigDecimal.valueOf(Math.abs(size)))
              .build());
    }
    return adapted;
  }

  private static LimitOrder level(
      PredictionMarketContract contract, OrderType type, BigDecimal price, Integer count) {
    return new LimitOrder.Builder(type, contract)
        .originalAmount(count == null ? null : BigDecimal.valueOf(count))
        .limitPrice(price)
        .build();
  }

  private static OrderType genericType(String action, String side) {
    boolean buy = "buy".equalsIgnoreCase(action);
    boolean yes = !"no".equalsIgnoreCase(side);
    // RULE_LEGACY_NO_COMPLEMENT: buy+no -> ASK YES, sell+no -> BID YES.
    return buy == yes ? OrderType.BID : OrderType.ASK;
  }

  private static BigDecimal genericPrice(
      String action, String side, Integer yesPrice, Integer noPrice) {
    if ("no".equalsIgnoreCase(side)) {
      // RULE_LEGACY_NO_COMPLEMENT: NO-side records price at the YES complement.
      return noPrice == null ? null : centsToDollars(100 - noPrice);
    }
    return centsToDollars(yesPrice);
  }

  private static Date parseTime(String iso) {
    return iso == null || iso.isBlank() ? null : Date.from(Instant.parse(iso));
  }
}
