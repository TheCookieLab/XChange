package org.knowm.xchange.coinbase;

import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetail;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBookEntry;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseMarketTrade;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandle;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Ticker.Builder;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

/**
 * jamespedwards42
 */
public final class CoinbaseAdapters {

  private CoinbaseAdapters() {
  }

  /**
   * Extract newly created order id from Coinbase createOrder response.
   */
  public static String adaptCreatedOrderId(CoinbaseCreateOrderResponse response) {
    return response == null ? null : response.getOrderId();
  }

  public static OrderBook adaptOrderBook(CoinbasePriceBook priceBook) {
    Instrument instrument = CoinbaseAdapters.adaptInstrument(priceBook.getProductId());

    List<LimitOrder> asks = priceBook.getAsks().stream().map(
        priceBookEntry -> CoinbaseAdapters.adaptOrderBookEntry(priceBookEntry, OrderType.ASK,
            instrument)).collect(Collectors.toList());

    List<LimitOrder> bids = priceBook.getBids().stream().map(
        priceBookEntry -> CoinbaseAdapters.adaptOrderBookEntry(priceBookEntry, OrderType.BID,
            instrument)).collect(Collectors.toList());

    return new OrderBook(
        Date.from(DateTimeFormatter.ISO_INSTANT.parse(priceBook.getTime(), Instant::from)), asks,
        bids);
  }

  public static LimitOrder adaptOrderBookEntry(CoinbasePriceBookEntry priceBookEntry,
      Order.OrderType orderType, Instrument instrument) {
    return new LimitOrder(orderType, priceBookEntry.getSize(), instrument, null, null,
        priceBookEntry.getPrice());
  }

  public static Trade adaptTrade(CoinbaseMarketTrade marketTrade) {
    return UserTrade.builder().id(marketTrade.getTradeId())
        .instrument(new CurrencyPair(marketTrade.getProductId())).price(marketTrade.getPrice())
        .originalAmount(marketTrade.getSize()).timestamp(
            Date.from(DateTimeFormatter.ISO_INSTANT.parse(marketTrade.getTime(), Instant::from)))
        .type(adaptOrderType(marketTrade.getSide())).build();
  }

  public static OrderType adaptOrderType(String side) {
    switch (side) {
      case "SELL":
        return OrderType.ASK;
      case "BUY":
        return OrderType.BID;
    }
    return null;
  }

  /**
   * Adapt a Coinbase Advanced Trade order detail to XChange Order (as a LimitOrder when price is present).
   */
  public static Order adaptOrder(CoinbaseOrderDetail detail) {
    if (detail == null) return null;
    Order.OrderType orderType = adaptOrderType(detail.getSide());
    if (detail.getPrice() != null) {
      return new LimitOrder(
          orderType,
          detail.getSize(),
          detail.getInstrument(),
          detail.getOrderId(),
          detail.getCreatedTime(),
          detail.getPrice(),
          detail.getAverageFilledPrice(),
          detail.getFilledSize(),
          detail.getTotalFees(),
          adaptOrderStatus(detail.getStatus()));
    }
    // Fallback to generic Order without limit price
    return new org.knowm.xchange.dto.trade.MarketOrder(
        orderType,
        detail.getSize(),
        detail.getInstrument(),
        detail.getOrderId(),
        detail.getCreatedTime());
  }

  private static Order.OrderStatus adaptOrderStatus(String status) {
    if (status == null) return Order.OrderStatus.UNKNOWN;
    switch (status.toUpperCase()) {
      case "OPEN":
      case "PENDING":
      case "NEW":
        return Order.OrderStatus.OPEN;
      case "FILLED":
      case "DONE":
        return Order.OrderStatus.FILLED;
      case "CANCELLED":
      case "CANCELED":
        return Order.OrderStatus.CANCELED;
      case "EXPIRED":
        return Order.OrderStatus.EXPIRED;
      case "REJECTED":
        return Order.OrderStatus.REJECTED;
      case "PARTIALLY_FILLED":
        return Order.OrderStatus.PARTIALLY_FILLED;
      default:
        return Order.OrderStatus.UNKNOWN;
    }
  }

  /**
   * Adapt Coinbase Advanced Trade list orders response into XChange OpenOrders (LimitOrders only),
   * filtering to orders in an open state.
   */
  public static OpenOrders adaptOpenOrders(CoinbaseListOrdersResponse response) {
    List<LimitOrder> open = response.getOrders().stream()
        .filter(detail -> {
          Order.OrderStatus s = adaptOrderStatus(detail.getStatus());
          return s != null && s.isOpen();
        })
        .map(CoinbaseAdapters::adaptOrder)
        .filter(o -> o instanceof LimitOrder)
        .map(o -> (LimitOrder) o)
        .collect(Collectors.toList());
    return new OpenOrders(open);
  }

  /**
   * Adapts the given financial instrument to a product ID string suitable for Coinbase API by
   * replacing any forward slashes in the instrument's string representation with hyphens.
   *
   * @param instrument the financial instrument to adapt, typically representing a currency pair or
   *                   derivative instrument
   * @return a product ID string with forward slashes replaced by hyphens, formatted according to
   * Coinbase's required conventions
   */
  public static String adaptProductId(Instrument instrument) {
    Objects.requireNonNull(instrument, "Cannot format productId from a null instrument");
    return instrument.toString().replace("/", "-");
  }

  /**
   * Adapts a product ID string into a financial instrument (e.g., CurrencyPair) by splitting the
   * string on hyphens. Expects the product ID to represent a currency pair in the format
   * "base-counter".
   *
   * @param productId the product ID string to adapt, must not be null
   * @return the corresponding Instrument (CurrencyPair) if the product ID contains exactly two
   * hyphen-separated tokens, or null if the product ID format is invalid
   */
  public static Instrument adaptInstrument(String productId) {
    Objects.requireNonNull(productId, "Cannot create instrument from a null productId");

    String[] tokens = productId.split("-");
    if (tokens.length == 2) {
      return new CurrencyPair(tokens[0], tokens[1]);
    }

    return null;
  }

  public static String adaptProductCandleGranularity(Long candleIntervalSeconds) {
    if (candleIntervalSeconds == null) {
      return null;
    }

    switch (candleIntervalSeconds.intValue()) {
      case 60:
        return "ONE_MINUTE";
      case 300:
        return "FIVE_MINUTE";
      case 900:
        return "FIFTEEN_MINUTE";
      case 1800:
        return "THIRTY_MINUTE";
      case 3600:
        return "ONE_HOUR";
      case 7200:
        return "TWO_HOUR";
      case 21_600:
        return "SIX_HOUR";
      case 86_400:
        return "ONE_DAY";
      default:
        return null;
    }
  }

  public static CandleStick adaptProductCandle(CoinbaseProductCandle productCandle) {
    return new CandleStick.Builder().open(productCandle.getOpen()).high(productCandle.getHigh())
        .low(productCandle.getLow()).close(productCandle.getClose())
        .volume(productCandle.getVolume())
        .timestamp(Date.from(Instant.ofEpochSecond(Long.parseLong(productCandle.getStart()))))
        .build();
  }

  public static Ticker adaptTicker(CoinbaseProductResponse product,
      CoinbaseProductCandlesResponse candle, CoinbasePriceBook priceBook) {
    Builder builder = new Ticker.Builder();

    if (product != null) {
      if (product.getPricePercentageChange24H() != null) {
        builder = builder.percentageChange(
            product.getPricePercentageChange24H().round(new MathContext(2, RoundingMode.HALF_EVEN)));
      }
      if (product.getVolume24H() != null) {
        builder = builder.volume(product.getVolume24H());
      }
      if (product.getApproximateQuoteVolume24H() != null) {
        builder = builder.quoteVolume(product.getApproximateQuoteVolume24H());
      }
    }

    if (priceBook != null && !priceBook.getAsks().isEmpty() && !priceBook.getBids().isEmpty()) {
      builder = builder.ask(priceBook.getAsks().get(0).getPrice())
          .askSize(priceBook.getAsks().get(0).getSize())
          .bid(priceBook.getBids().get(0).getPrice())
          .bidSize(priceBook.getBids().get(0).getSize())
          .instrument(adaptInstrument(priceBook.getProductId())).timestamp(
              Date.from(DateTimeFormatter.ISO_INSTANT.parse(priceBook.getTime(), Instant::from)));
    }

    if (candle != null && !candle.getCandles().isEmpty()) {
      builder = builder.low(candle.getCandles().get(0).getLow())
          .high(candle.getCandles().get(0).getHigh())
          .open(candle.getCandles().get(0).getOpen())
          .last(candle.getCandles().get(0).getClose());
    }

    return builder.build();
  }
}
