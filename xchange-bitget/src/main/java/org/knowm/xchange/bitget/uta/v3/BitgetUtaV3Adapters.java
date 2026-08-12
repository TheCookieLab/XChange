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
import org.knowm.xchange.instrument.Instrument;

/**
 * Conversions between Bitget UTA v3 wire DTOs and XChange core DTOs.
 *
 * <p>Instrument identity rules: SPOT and MARGIN share the spot instrument universe (margin rows map
 * to the same {@link CurrencyPair} as their spot twin to avoid equal-symbol collisions); futures
 * map to {@link FuturesContract} whose prompt preserves the derivative identity (perpetuals →
 * {@code "PERP"}).
 */
@UtilityClass
public class BitgetUtaV3Adapters {

  /** Symbol text for a v3 request, e.g. {@code BTCUSDT}. */
  public String toString(Instrument instrument) {
    if (instrument instanceof FuturesContract) {
      FuturesContract contract = (FuturesContract) instrument;
      return contract.getCurrencyPair().getBase().getCurrencyCode()
          + contract.getCurrencyPair().getCounter().getCurrencyCode();
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
   * XChange instrument for a v3 instrument row. Derivative rows become {@link FuturesContract} with
   * prompt {@code PERP} for perpetuals; spot/margin rows become the plain {@link CurrencyPair}.
   */
  public Instrument toInstrument(BitgetUtaV3Instrument dto) {
    CurrencyPair pair =
        new CurrencyPair(
            Currency.getInstance(dto.getBaseCoin()), Currency.getInstance(dto.getQuoteCoin()));
    if (BitgetUtaV3Category.fromWireName(dto.getCategory()).isDerivative()) {
      return new FuturesContract(pair, "PERP");
    }
    return pair;
  }

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
        .percentageChange(dto.getPrice24hPcnt())
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

  /** XChange order for a v3 order DTO; instrument must be resolved by the caller. */
  public Order toOrder(BitgetUtaV3Order dto, Instrument instrument) {
    OrderType orderType = toOrderType(dto.getSide());
    Order.Builder builder;
    if ("market".equals(dto.getOrderType())) {
      builder = new MarketOrder.Builder(orderType, instrument);
    } else {
      builder = new LimitOrder.Builder(orderType, instrument).limitPrice(dto.getPrice());
    }
    return builder
        .id(dto.getOrderId())
        .userReference(dto.getClientOid())
        .originalAmount(dto.getQty())
        .cumulativeAmount(dto.getCumExecQty())
        .averagePrice(dto.getAvgPrice())
        .timestamp(toDate(dto.getCreatedTime()))
        .orderStatus(toOrderStatus(dto.getOrderStatus()))
        .build();
  }

  /** XChange user trade for a v3 fill DTO; instrument must be resolved by the caller. */
  public UserTrade toUserTrade(BitgetUtaV3Fill dto, Instrument instrument) {
    BigDecimal fee = null;
    Currency feeCurrency = null;
    if (dto.getFeeDetail() != null) {
      for (BitgetUtaV3Order.BitgetUtaV3Fee detail : dto.getFeeDetail()) {
        if (detail.getFee() != null) {
          fee = fee == null ? detail.getFee() : fee.add(detail.getFee());
        }
        if (feeCurrency == null && detail.getFeeCoin() != null) {
          feeCurrency = Currency.getInstance(detail.getFeeCoin());
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
    BitgetUtaV3Category category = toCategory(limitOrder.getInstrument());
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
      builder.marginMode("crossed").holdMode("one_way_mode");
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
   */
  public BitgetUtaV3PlaceOrderRequest toPlaceOrderRequest(MarketOrder marketOrder) {
    BitgetUtaV3Category category = toCategory(marketOrder.getInstrument());
    BitgetUtaV3PlaceOrderRequest.BitgetUtaV3PlaceOrderRequestBuilder builder =
        BitgetUtaV3PlaceOrderRequest.builder()
            .category(category.getWireName())
            .symbol(toString(marketOrder.getInstrument()))
            .side(toSide(marketOrder.getType()))
            .orderType("market")
            .qty(marketOrder.getOriginalAmount())
            .clientOid(marketOrder.getUserReference());
    if (category.isDerivative()) {
      builder.marginMode("crossed").holdMode("one_way_mode");
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
