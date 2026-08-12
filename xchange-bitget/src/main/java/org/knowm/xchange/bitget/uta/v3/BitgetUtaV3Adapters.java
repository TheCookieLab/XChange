package org.knowm.xchange.bitget.uta.v3;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Instrument;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3OrderBook;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Ticker;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3CancelOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Fill;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3PlaceOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Position;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;

/**
 * Conversions between Bitget UTA v3 wire DTOs and XChange core DTOs.
 *
 * <p>Instrument identity rules: SPOT and MARGIN share the spot instrument universe (margin rows map
 * to the same {@link CurrencyPair} as their spot twin to avoid equal-symbol collisions); futures
 * map to {@link FuturesContract} whose prompt preserves the derivative identity (perpetuals →
 * {@code "PERP"}, dated delivery contracts → the provider's expiry suffix such as {@code "1226"}),
 * and {@link #toString} reproduces the provider symbol so catalog and requests round-trip.
 */
@UtilityClass
public class BitgetUtaV3Adapters {

  /** Formats a delivery epoch-millis into the {@code MMdd} prompt shape. */
  private static final DateTimeFormatter DELIVERY_PROMPT_FORMATTER =
      DateTimeFormatter.ofPattern("MMdd").withZone(ZoneOffset.UTC);

  /**
   * Symbol text for a v3 request, e.g. {@code BTCUSDT}. Dated delivery contracts keep their
   * provider-encoded expiry suffix (a {@code 1226} prompt yields {@code BTCUSD1226}) so requests
   * made with the mapped instrument target the catalog symbol rather than the unsuffixed
   * perpetual twin.
   */
  public String toString(Instrument instrument) {
    if (instrument instanceof FuturesContract) {
      FuturesContract contract = (FuturesContract) instrument;
      String symbol =
          contract.getCurrencyPair().getBase().getCurrencyCode()
              + contract.getCurrencyPair().getCounter().getCurrencyCode();
      if (!"PERP".equals(contract.getPrompt())) {
        symbol += contract.getPrompt();
      }
      return symbol;
    }
    CurrencyPair pair = (CurrencyPair) instrument;
    return pair.getBase().getCurrencyCode() + pair.getCounter().getCurrencyCode();
  }

  /**
   * Category for a request. Futures pick their product family from the quote currency: USDT →
   * usdt-futures, USDC → usdc-futures, anything else (e.g. USD coin-margined) → coin-futures.
   */
  public BitgetUtaV3Category toCategory(Instrument instrument) {
    if (instrument instanceof FuturesContract) {
      Currency counter = ((FuturesContract) instrument).getCurrencyPair().getCounter();
      if (Currency.USDT.equals(counter)) {
        return BitgetUtaV3Category.USDT_FUTURES;
      }
      if (Currency.USDC.equals(counter)) {
        return BitgetUtaV3Category.USDC_FUTURES;
      }
      return BitgetUtaV3Category.COIN_FUTURES;
    }
    return BitgetUtaV3Category.SPOT;
  }

  /**
   * Category for a place-order request: {@link #toCategory(Instrument)} plus the caller-controlled
   * {@link BitgetUtaV3OrderFlags#MARGIN} override. Margin orders are only valid on spot-family
   * instruments; a futures instrument keeps its derivative category even when the flag is set.
   */
  private BitgetUtaV3Category toPlaceOrderCategory(Instrument instrument, Order order) {
    BitgetUtaV3Category category = toCategory(instrument);
    if (!(instrument instanceof FuturesContract)
        && order.hasFlag(BitgetUtaV3OrderFlags.MARGIN)) {
      return BitgetUtaV3Category.MARGIN;
    }
    return category;
  }

  /**
   * XChange instrument for a v3 instrument row. Derivative rows become {@link FuturesContract} —
   * prompt {@code PERP} for perpetuals, the provider's expiry suffix (e.g. {@code 1226} for
   * {@code BTCUSD1226}) for dated delivery contracts, so a delivery contract and a perpetual on
   * the same pair never collapse to one catalog key. Spot/margin rows become the plain {@link
   * CurrencyPair}.
   */
  public Instrument toInstrument(BitgetUtaV3Instrument dto) {
    CurrencyPair pair =
        new CurrencyPair(
            Currency.getInstance(dto.getBaseCoin()), Currency.getInstance(dto.getQuoteCoin()));
    if (BitgetUtaV3Category.fromWireName(dto.getCategory()).isDerivative()) {
      return new FuturesContract(pair, derivativePrompt(dto));
    }
    return pair;
  }

  /**
   * Contract prompt for a derivative instrument row: {@code PERP} for perpetuals; for dated
   * delivery contracts (non-blank {@code deliveryTime}) the expiry suffix the provider embeds in
   * the symbol ({@code BTCUSD1226} → {@code 1226}), falling back to the {@code MMdd} expiry
   * derived from {@code deliveryTime} when the symbol carries no suffix.
   */
  private static String derivativePrompt(BitgetUtaV3Instrument dto) {
    String deliveryTime = dto.getDeliveryTime();
    if (deliveryTime == null || deliveryTime.isBlank()) {
      return "PERP";
    }
    String symbol = dto.getSymbol();
    String prefix = dto.getBaseCoin() + dto.getQuoteCoin();
    if (symbol != null && symbol.startsWith(prefix) && symbol.length() > prefix.length()) {
      // the provider encodes the expiry in the symbol itself; prefer it so the reverse formatter
      // reproduces the catalog symbol exactly
      return symbol.substring(prefix.length());
    }
    try {
      return Instant.ofEpochMilli(Long.parseLong(deliveryTime))
          .atZone(ZoneOffset.UTC)
          .format(DELIVERY_PROMPT_FORMATTER);
    } catch (NumberFormatException e) {
      return "PERP";
    }
  }

  /**
   * XChange ticker for a v3 ticker row. The provider's {@code price24hPcnt} is a decimal fraction
   * ({@code 0.0345} = 3.45%) while {@link Ticker.Builder#percentageChange} contracts percentage
   * units ({@code 1} = 1%), so the wire value is scaled by 100.
   */
  public Ticker toTicker(BitgetUtaV3Ticker dto, Instrument instrument) {
    return new Ticker.Builder()
        .instrument(instrument)
        .open(dto.getOpenPrice24h())
        .last(dto.getLastPrice())
        .bid(dto.getBid1Price())
        .ask(dto.getAsk1Price())
        .high(dto.getHighPrice24h())
        .low(dto.getLowPrice24h())
        .volume(dto.getVolume24h())
        .quoteVolume(dto.getTurnover24h())
        .timestamp(toDate(dto.getTs()))
        .bidSize(dto.getBid1Size())
        .askSize(dto.getAsk1Size())
        .percentageChange(
            dto.getPrice24hPcnt() == null
                ? null
                : dto.getPrice24hPcnt().movePointRight(2))
        .build();
  }

  public OrderBook toOrderBook(BitgetUtaV3OrderBook dto, Instrument instrument) {
    List<LimitOrder> asks = new ArrayList<>();
    List<LimitOrder> bids = new ArrayList<>();
    if (dto.getAsks() != null) {
      for (BigDecimal[] level : dto.getAsks()) {
        asks.add(
            new LimitOrder.Builder(OrderType.ASK, instrument)
                .limitPrice(level[0])
                .originalAmount(level[1])
                .build());
      }
    }
    if (dto.getBids() != null) {
      for (BigDecimal[] level : dto.getBids()) {
        bids.add(
            new LimitOrder.Builder(OrderType.BID, instrument)
                .limitPrice(level[0])
                .originalAmount(level[1])
                .build());
      }
    }
    return new OrderBook(toDate(dto.getTs()), asks, bids);
  }

  /**
   * XChange order for a v3 order DTO; instrument must be resolved by the caller.
   *
   * <p>The provider's {@code qty} is the quote-coin spend for spot/margin market buys, so {@link
   * Order#getOriginalAmount()} (always base-denominated in XChange) is taken from the
   * base-denominated executed quantity {@code cumExecQty} for those orders instead — the only base
   * figure the provider returns, zero while the order is live. Every other order shape maps {@code
   * qty} unchanged.
   */
  public Order toOrder(BitgetUtaV3Order dto, Instrument instrument) {
    OrderType orderType = toOrderType(dto.getSide());
    boolean quoteDenominatedMarketBuy =
        "market".equals(dto.getOrderType())
            && orderType == OrderType.BID
            && ("spot".equalsIgnoreCase(dto.getCategory())
                || "margin".equalsIgnoreCase(dto.getCategory()));
    Order.Builder builder;
    if ("market".equals(dto.getOrderType())) {
      builder = new MarketOrder.Builder(orderType, instrument);
    } else {
      builder = new LimitOrder.Builder(orderType, instrument).limitPrice(dto.getPrice());
    }
    return builder
        .id(dto.getOrderId())
        .userReference(dto.getClientOid())
        .originalAmount(quoteDenominatedMarketBuy ? dto.getCumExecQty() : dto.getQty())
        .cumulativeAmount(dto.getCumExecQty())
        .averagePrice(dto.getAvgPrice())
        .timestamp(toDate(dto.getCreatedTime()))
        .orderStatus(toOrderStatus(dto.getOrderStatus()))
        .build();
  }

  /**
   * XChange user trade for a v3 fill DTO; instrument must be resolved by the caller.
   *
   * <p>Fee detail may carry entries in several currencies (e.g. a discount token plus the trading
   * currency). {@link UserTrade} carries a single fee amount and currency, so only entries sharing
   * the first fee coin are summed; entries in other denominations are excluded rather than added
   * across currencies and mislabeled.
   */
  public UserTrade toUserTrade(BitgetUtaV3Fill dto, Instrument instrument) {
    BigDecimal fee = null;
    Currency feeCurrency = null;
    if (dto.getFeeDetail() != null && !dto.getFeeDetail().isEmpty()) {
      String feeCoin = null;
      for (BitgetUtaV3Order.BitgetUtaV3Fee detail : dto.getFeeDetail()) {
        if (detail.getFeeCoin() == null || detail.getFee() == null) {
          continue;
        }
        if (feeCoin == null) {
          feeCoin = detail.getFeeCoin();
          feeCurrency = Currency.getInstance(feeCoin);
        }
        if (feeCoin.equals(detail.getFeeCoin())) {
          fee = fee == null ? detail.getFee() : fee.add(detail.getFee());
        }
      }
    }
    return UserTrade.builder()
        .type(toOrderType(dto.getSide()))
        .originalAmount(dto.getExecQty())
        .instrument(instrument)
        .price(dto.getExecPrice())
        .timestamp(toDate(dto.getCreatedTime()))
        .id(dto.getExecId())
        .orderId(dto.getOrderId())
        .orderUserReference(dto.getClientOid())
        .feeAmount(fee)
        .feeCurrency(feeCurrency)
        .build();
  }

  /** XChange open position for a v3 position DTO; instrument must be resolved by the caller. */
  public OpenPosition toOpenPosition(BitgetUtaV3Position dto, Instrument instrument) {
    return OpenPosition.builder()
        .instrument(instrument)
        .type("long".equals(dto.getPosSide()) ? OpenPosition.Type.LONG : OpenPosition.Type.SHORT)
        .marginMode(
            "isolated".equals(dto.getMarginMode())
                ? OpenPosition.MarginMode.ISOLATED
                : OpenPosition.MarginMode.CROSS)
        .size(dto.getTotal())
        .price(dto.getAvgPrice())
        .liquidationPrice(dto.getLiquidationPrice())
        .unRealisedPnl(dto.getUnrealisedPnl())
        .createdAt(toInstant(dto.getCreatedTime()))
        .updatedAt(toInstant(dto.getUpdatedTime()))
        .build();
  }

  /** v3 place-order request for a limit order. */
  public BitgetUtaV3PlaceOrderRequest toPlaceOrderRequest(LimitOrder limitOrder) {
    BitgetUtaV3Category category =
        toPlaceOrderCategory(limitOrder.getInstrument(), limitOrder);
    BitgetUtaV3PlaceOrderRequest.BitgetUtaV3PlaceOrderRequestBuilder builder =
        BitgetUtaV3PlaceOrderRequest.builder()
            .category(category.getWireName())
            .symbol(toString(limitOrder.getInstrument()))
            .side(toSide(limitOrder.getType()))
            .orderType("limit")
            .price(limitOrder.getLimitPrice())
            .qty(limitOrder.getOriginalAmount())
            .timeInForce("gtc")
            .clientOid(limitOrder.getUserReference());
    if (category.isDerivative()) {
      builder.marginMode(
              limitOrder.hasFlag(BitgetUtaV3OrderFlags.ISOLATED_MARGIN) ? "isolated" : "crossed")
          .holdMode(
              limitOrder.hasFlag(BitgetUtaV3OrderFlags.HEDGE_MODE)
                  ? "hedge_mode"
                  : "one_way_mode");
      if (limitOrder.hasFlag(BitgetUtaV3OrderFlags.HEDGE_MODE)) {
        builder.posSide(toPosSide(limitOrder));
      }
      if (limitOrder.hasFlag(BitgetUtaV3OrderFlags.REDUCE_ONLY)) {
        builder.reduceOnly("yes");
      }
    }
    return builder.build();
  }

  /**
   * v3 place-order request for a market order.
   *
   * <p>The v3 endpoint ({@code POST /api/v3/trade/place-order}) accepts exactly one size parameter,
   * the required {@code qty}; there is no {@code amount} parameter. Per the official docs {@code
   * qty} is the base-coin quantity for limit and market-sell orders and the quote-coin spend for
   * market-buy orders on spot/margin categories.
   *
   * <p>Because XChange's {@link MarketOrder#getOriginalAmount()} is always base-denominated, a
   * spot/margin market buy must not be sent as-is: Bitget would spend {@code originalAmount} quote
   * coins (a 0.1-BTC order becomes a 0.1-USDT spend). Callers must set {@link
   * BitgetUtaV3OrderFlags#MARKET_BUY_QUOTE_AMOUNT} to declare that the amount is the quote-coin
   * spend; without it the order fails here, before any request is sent. Market sells and futures
   * market orders keep the standard base semantics.
   *
   * @throws ExchangeException for a spot/margin market buy without {@link
   *     BitgetUtaV3OrderFlags#MARKET_BUY_QUOTE_AMOUNT}
   */
  public BitgetUtaV3PlaceOrderRequest toPlaceOrderRequest(MarketOrder marketOrder) {
    BitgetUtaV3Category category =
        toPlaceOrderCategory(marketOrder.getInstrument(), marketOrder);
    if (!category.isDerivative()
        && marketOrder.getType() == OrderType.BID
        && !marketOrder.hasFlag(BitgetUtaV3OrderFlags.MARKET_BUY_QUOTE_AMOUNT)) {
      throw new ExchangeException(
          "Bitget v3 market-buy orders spend the quote coin (the required qty parameter is the "
              + "quote-amount), while XChange MarketOrder.originalAmount is base-denominated; set "
              + "BitgetUtaV3OrderFlags.MARKET_BUY_QUOTE_AMOUNT to place a spot/margin market buy "
              + "whose originalAmount is the quote-coin spend, or use a limit order");
    }
    BitgetUtaV3PlaceOrderRequest.BitgetUtaV3PlaceOrderRequestBuilder builder =
        BitgetUtaV3PlaceOrderRequest.builder()
            .category(category.getWireName())
            .symbol(toString(marketOrder.getInstrument()))
            .side(toSide(marketOrder.getType()))
            .orderType("market")
            .qty(marketOrder.getOriginalAmount())
            .clientOid(marketOrder.getUserReference());
    if (category.isDerivative()) {
      builder.marginMode(
              marketOrder.hasFlag(BitgetUtaV3OrderFlags.ISOLATED_MARGIN) ? "isolated" : "crossed")
          .holdMode(
              marketOrder.hasFlag(BitgetUtaV3OrderFlags.HEDGE_MODE)
                  ? "hedge_mode"
                  : "one_way_mode");
      if (marketOrder.hasFlag(BitgetUtaV3OrderFlags.HEDGE_MODE)) {
        builder.posSide(toPosSide(marketOrder));
      }
      if (marketOrder.hasFlag(BitgetUtaV3OrderFlags.REDUCE_ONLY)) {
        builder.reduceOnly("yes");
      }
    }
    return builder.build();
  }

  /** v3 cancel-order request for an order id (XChange's standard cancel identity). */
  public BitgetUtaV3CancelOrderRequest toCancelOrderRequest(String orderId) {
    return BitgetUtaV3CancelOrderRequest.builder().orderId(orderId).build();
  }

  private static OrderType toOrderType(String side) {
    return "buy".equals(side) ? OrderType.BID : OrderType.ASK;
  }

  private static String toSide(OrderType orderType) {
    return orderType == OrderType.BID ? "buy" : "sell";
  }

  /**
   * Position side for a hedge-mode futures order.
   *
   * <p>In two-way position mode a bare buy/sell is ambiguous — it can open one side or close the
   * other — so Bitget requires an explicit {@code posSide}. XChange core carries no position-side
   * field, so callers must declare it with {@link BitgetUtaV3OrderFlags#POS_SIDE_LONG} or {@link
   * BitgetUtaV3OrderFlags#POS_SIDE_SHORT}; a hedge-mode order without exactly one of them fails
   * before any request is sent.
   *
   * @throws ExchangeException when neither or both position-side flags are set on a hedge-mode order
   */
  private static String toPosSide(Order order) {
    boolean longSide = order.hasFlag(BitgetUtaV3OrderFlags.POS_SIDE_LONG);
    boolean shortSide = order.hasFlag(BitgetUtaV3OrderFlags.POS_SIDE_SHORT);
    if (longSide == shortSide) {
      throw new ExchangeException(
          "Hedge-mode futures orders require exactly one of POS_SIDE_LONG or POS_SIDE_SHORT");
    }
    return longSide ? "long" : "short";
  }

  private static OrderStatus toOrderStatus(String status) {
    switch (status) {
      case "live":
      case "new":
        return OrderStatus.NEW;
      case "partially_filled":
        return OrderStatus.PARTIALLY_FILLED;
      case "filled":
        return OrderStatus.FILLED;
      case "cancelled":
        return OrderStatus.CANCELED;
      default:
        return OrderStatus.UNKNOWN;
    }
  }

  private static Instant toInstant(String epochMillis) {
    if (epochMillis == null || epochMillis.isEmpty()) {
      return null;
    }
    try {
      return Instant.ofEpochMilli(Long.parseLong(epochMillis));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public Date toDate(Long epochMillis) {
    return Optional.ofNullable(epochMillis).map(Date::new).orElse(null);
  }

  public Date toDate(String epochMillis) {
    if (epochMillis == null || epochMillis.isEmpty()) {
      return null;
    }
    try {
      return new Date(Long.parseLong(epochMillis));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
