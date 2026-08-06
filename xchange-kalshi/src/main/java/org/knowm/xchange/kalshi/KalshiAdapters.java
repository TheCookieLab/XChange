package org.knowm.xchange.kalshi;

import java.math.BigDecimal;
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
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket.KalshiPriceRange;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse.KalshiOrderBookLevels;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiTradesResponse.KalshiTradeRecord;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse.KalshiFill;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrder;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderFlags;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderRequest;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Conversions between Kalshi wire DTOs and generic XChange DTOs.
 *
 * <p>The Kalshi read surface is fixed-point: prices are dollar strings with up to 4 decimal
 * places ({@code *_dollars}), counts are contract strings with up to 2 decimal places
 * ({@code *_fp}), and order/fill/trade direction is the canonical {@code book_side} field. See
 * <a href="https://docs.kalshi.com/getting_started/fixed_point_migration">Fixed-Point
 * Representation</a> and <a href="https://docs.kalshi.com/getting_started/order_direction">Order
 * direction</a>.
 *
 * <p>Named provider rules (each enforced by an adapter test):
 *
 * <ul>
 *   <li>{@link #RULE_YES_LEG_ONLY} — generic {@code BID} maps to native {@code bid} (buy YES),
 *       generic {@code ASK} maps to native {@code ask} (sell YES).
 *   <li>{@link #RULE_NO_BID_COMPLEMENT} — on the order-book read surface, Kalshi NO bids are YES
 *       asks at the complement price {@code 1 - noPrice} dollars.
 *   <li>{@link #RULE_BOOK_SIDE_DIRECTION} — order/fill/trade reads derive direction from the
 *       canonical {@code book_side}: {@code bid} is generic {@code BID}, {@code ask} is generic
 *       {@code ASK}; prices are always read from {@code yes_price_dollars}, which the provider
 *       quotes on the YES leg for every direction.
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
      "Kalshi NO bids are YES asks at the complement price (1 - no_price) dollars.";

  /** Named provider rule: canonical direction and price on the order/fill/trade read surface. */
  public static final String RULE_BOOK_SIDE_DIRECTION =
      "Kalshi order/fill/trade reads use the canonical book_side field: 'bid' (outcome_side"
          + " 'yes') maps to generic BID (buy YES) and 'ask' (outcome_side 'no') maps to generic"
          + " ASK (sell YES). All prices are quoted on the YES leg via yes_price_dollars, so a"
          + " 'buy NO at q' record reads as an ASK YES at (1 - q) and a 'sell NO' as a BID YES at"
          + " (1 - q).";

  /** Named provider rule: explicit NO-leg placement is rejected, never complemented. */
  public static final String RULE_SIDE_NO_REJECTED =
      "Kalshi SIDE_NO order flag is rejected: NO-leg placement is not available through the"
          + " generic API and is never silently complemented into a YES order.";

  /** Maximum decimal places on a fixed-point price string ({@code *_dollars}). */
  static final int PRICE_DECIMALS = 4;

  /** Maximum decimal places on a fixed-point count string ({@code *_fp}). */
  static final int COUNT_DECIMALS = 2;

  private static final BigDecimal ONE = BigDecimal.ONE;

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

  /**
   * Converts a generic dollar limit price to the V2 fixed-point dollar string without rounding:
   * the submitted value is preserved verbatim. Prices must be strictly between 0 and 1 dollars
   * and representable with at most {@link #PRICE_DECIMALS} decimal places, matching Kalshi's
   * fixed-point price strings.
   *
   * @param price generic limit price
   * @return plain fixed-point dollar string
   * @throws IllegalArgumentException if the price is not representable on Kalshi's price grid
   */
  static String toKalshiPriceString(BigDecimal price) {
    if (price == null
        || price.compareTo(BigDecimal.ZERO) <= 0
        || price.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalArgumentException(
          "Kalshi limit price must be between 0 and 1 dollars exclusive: " + price);
    }
    if (price.stripTrailingZeros().scale() > PRICE_DECIMALS) {
      throw new IllegalArgumentException(
          "Kalshi prices are fixed-point dollar strings with at most "
              + PRICE_DECIMALS
              + " decimal places; "
              + price
              + " cannot be represented without rounding");
    }
    return price.toPlainString();
  }

  /**
   * Converts a generic contract count to the V2 fixed-point count string without rounding: the
   * submitted value is preserved verbatim. Counts must be positive and representable with at
   * most {@link #COUNT_DECIMALS} decimal places, matching Kalshi's fixed-point count strings
   * (minimum granularity 0.01 contracts).
   *
   * @param amount generic contract count
   * @return plain fixed-point count string
   * @throws IllegalArgumentException if the count is not representable
   */
  static String toKalshiCountString(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Kalshi contract count must be positive: " + amount);
    }
    if (amount.stripTrailingZeros().scale() > COUNT_DECIMALS) {
      throw new IllegalArgumentException(
          "Kalshi contract counts are fixed-point strings with at most "
              + COUNT_DECIMALS
              + " decimal places; "
              + amount
              + " cannot be represented without rounding");
    }
    return amount.toPlainString();
  }

  /**
   * Adapts a market record to exchange metadata, deriving the price grid from the market's
   * {@code price_ranges} instead of assuming a one-cent tick: the step size is the finest tick
   * across the market's valid price bands (the provider snaps order prices to the band
   * {@code step}). Fractional contracts are supported, so the amount step and minimum order size
   * are 0.01 contracts; the contract value is the market's {@code notional_value_dollars} (the
   * value of a single contract at settlement, 1 dollar for binary markets).
   *
   * @param market market record
   * @return generic instrument metadata
   */
  public static InstrumentMetaData adaptMetadata(KalshiMarket market) {
    return InstrumentMetaData.builder()
        .priceScale(PRICE_DECIMALS)
        .volumeScale(COUNT_DECIMALS)
        .priceStepSize(priceStepSize(market))
        .amountStepSize(new BigDecimal("0.01"))
        .minimumAmount(new BigDecimal("0.01"))
        .contractValue(contractValue(market))
        .build();
  }

  /** Adapts a market record to a generic YES-leg ticker. */
  public static Ticker adaptTicker(KalshiMarket market) {
    return new Ticker.Builder()
        .instrument(adaptContract(market))
        .bid(parseFixedPoint(market.yesBidDollars()))
        .ask(parseFixedPoint(market.yesAskDollars()))
        .last(parseFixedPoint(market.lastPriceDollars()))
        .volume(parseFixedPoint(market.volumeFp()))
        .build();
  }

  /**
   * Adapts the YES/NO order book to generic YES-leg depth, applying {@link
   * #RULE_NO_BID_COMPLEMENT}. Bids are sorted best-first, asks are normalized to best-first as
   * well.
   */
  public static OrderBook adaptOrderBook(String ticker, KalshiOrderBookResponse response) {
    PredictionMarketContract contract = contractForTicker(ticker);
    List<LimitOrder> bids = new ArrayList<>();
    List<LimitOrder> asks = new ArrayList<>();
    KalshiOrderBookLevels book = response.orderbookFp();
    if (book != null) {
      if (book.yesDollars() != null) {
        for (List<String> level : book.yesDollars()) {
          bids.add(
              level(contract, OrderType.BID, parseFixedPoint(level.get(0)), parseFixedPoint(level.get(1))));
        }
      }
      if (book.noDollars() != null) {
        for (List<String> level : book.noDollars()) {
          asks.add(
              level(
                  contract,
                  OrderType.ASK,
                  ONE.subtract(parseFixedPoint(level.get(0))),
                  parseFixedPoint(level.get(1))));
        }
      }
    }
    bids.sort(Comparator.comparing(LimitOrder::getLimitPrice).reversed());
    asks.sort(Comparator.comparing(LimitOrder::getLimitPrice));
    return new OrderBook(null, asks, bids);
  }

  /** Adapts public trades; a {@code ask} taker side reads as an ask-side aggressor on YES. */
  public static Trades adaptTrades(List<KalshiTradeRecord> trades) {
    List<Trade> adapted = new ArrayList<>();
    for (KalshiTradeRecord trade : trades) {
      adapted.add(
          Trade.builder()
              .type("ask".equalsIgnoreCase(trade.takerBookSide()) ? OrderType.ASK : OrderType.BID)
              .originalAmount(parseFixedPoint(trade.countFp()))
              .instrument(contractForTicker(trade.ticker()))
              .price(parseFixedPoint(trade.yesPriceDollars()))
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
   * callers control retry identity. The limit price and count are preserved verbatim and rejected
   * with {@link IllegalArgumentException} when they cannot be represented on Kalshi's fixed-point
   * grid without rounding.
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

  /** Maps an order record to a generic limit order per {@link #RULE_BOOK_SIDE_DIRECTION}. */
  public static LimitOrder adaptOrder(KalshiOrder order) {
    OrderType type = "ask".equalsIgnoreCase(order.bookSide()) ? OrderType.ASK : OrderType.BID;
    BigDecimal fillCount = parseFixedPoint(order.fillCountFp());
    LimitOrder.Builder builder =
        new LimitOrder.Builder(type, contractForTicker(order.ticker()))
            .originalAmount(parseFixedPoint(order.initialCountFp()))
            .limitPrice(parseFixedPoint(order.yesPriceDollars()))
            .id(order.orderId())
            .userReference(order.clientOrderId())
            .timestamp(parseTime(order.createdTime()))
            .orderStatus(adaptOrderStatus(order));
    if (fillCount != null && fillCount.signum() > 0) {
      builder.cumulativeAmount(fillCount);
    }
    return builder.build();
  }

  /** Maps a fill record to a generic user trade per {@link #RULE_BOOK_SIDE_DIRECTION}. */
  public static UserTrade adaptFill(KalshiFill fill) {
    return UserTrade.builder()
        .type("ask".equalsIgnoreCase(fill.bookSide()) ? OrderType.ASK : OrderType.BID)
        .originalAmount(parseFixedPoint(fill.countFp()))
        .instrument(contractForTicker(fill.ticker()))
        .price(parseFixedPoint(fill.yesPriceDollars()))
        .timestamp(parseTime(fill.createdTime()))
        .id(fill.fillId())
        .orderId(fill.orderId())
        .build();
  }

  /** Adapts the provider lifecycle status to the generic order status. */
  public static org.knowm.xchange.dto.Order.OrderStatus adaptOrderStatus(KalshiOrder order) {
    String status = order.status() == null ? "" : order.status();
    BigDecimal fillCount = parseFixedPoint(order.fillCountFp());
    return switch (status) {
      case "resting" ->
          fillCount != null && fillCount.signum() > 0
              ? org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED
              : org.knowm.xchange.dto.Order.OrderStatus.OPEN;
      case "canceled" -> org.knowm.xchange.dto.Order.OrderStatus.CANCELED;
      case "executed" -> org.knowm.xchange.dto.Order.OrderStatus.FILLED;
      default -> org.knowm.xchange.dto.Order.OrderStatus.UNKNOWN;
    };
  }

  /** Adapts the portfolio balance to a single USD wallet; the amount is a dollar string. */
  public static AccountInfo adaptAccountInfo(KalshiBalanceResponse balance) {
    BigDecimal available = parseFixedPoint(balance.balanceDollars());
    if (available == null) {
      available = BigDecimal.ZERO;
    }
    Wallet wallet =
        new Wallet(
            null,
            null,
            List.of(new Balance(Currency.USD, available, available)),
            null,
            null,
            null);
    return new AccountInfo(wallet);
  }

  /** Adapts market positions; negative YES counts map to SHORT (net NO exposure). */
  public static List<OpenPosition> adaptPositions(List<KalshiMarketPosition> positions) {
    List<OpenPosition> adapted = new ArrayList<>();
    for (KalshiMarketPosition position : positions) {
      BigDecimal size = parseFixedPoint(position.positionFp());
      if (size == null) {
        size = BigDecimal.ZERO;
      }
      adapted.add(
          OpenPosition.builder()
              .instrument(contractForTicker(position.ticker()))
              .type(size.signum() < 0 ? OpenPosition.Type.SHORT : OpenPosition.Type.LONG)
              .size(size.abs())
              .build());
    }
    return adapted;
  }

  /**
   * Parses a Kalshi fixed-point string ({@code *_dollars} or {@code *_fp}) into a {@link
   * BigDecimal}, preserving the provider-emitted scale, or {@code null} when absent.
   */
  static BigDecimal parseFixedPoint(String raw) {
    return raw == null ? null : new BigDecimal(raw);
  }

  private static BigDecimal priceStepSize(KalshiMarket market) {
    BigDecimal finest = null;
    if (market.priceRanges() != null) {
      for (KalshiPriceRange range : market.priceRanges()) {
        if (range.step() == null) {
          continue;
        }
        BigDecimal step = new BigDecimal(range.step());
        if (step.signum() > 0 && (finest == null || step.compareTo(finest) < 0)) {
          finest = step;
        }
      }
    }
    // Fall back to the whole-cent tick when the market omits its price grid.
    return finest == null ? new BigDecimal("0.01") : finest;
  }

  private static BigDecimal contractValue(KalshiMarket market) {
    BigDecimal notional = parseFixedPoint(market.notionalValueDollars());
    return notional == null ? ONE : notional;
  }

  private static LimitOrder level(
      PredictionMarketContract contract, OrderType type, BigDecimal price, BigDecimal count) {
    return new LimitOrder.Builder(type, contract)
        .originalAmount(count)
        .limitPrice(price)
        .build();
  }

  private static Date parseTime(String iso) {
    return iso == null || iso.isBlank() ? null : Date.from(Instant.parse(iso));
  }
}
