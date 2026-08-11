package org.knowm.xchange.kucoin.uta;

import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.Ordering;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.uta.dto.UtaExecution;
import org.knowm.xchange.kucoin.uta.dto.UtaPosition;
import org.knowm.xchange.kucoin.uta.dto.UtaCurrencyAsset;
import org.knowm.xchange.kucoin.uta.dto.UtaInstrument;
import org.knowm.xchange.kucoin.uta.dto.UtaOrder;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderBook;
import org.knowm.xchange.kucoin.uta.dto.UtaTicker;
import org.knowm.xchange.kucoin.uta.dto.UtaTrade;

/**
 * Mappings between UTA wire models and XChange DTOs.
 *
 * <p>Instrument identity is lossless: spot instruments map to {@link CurrencyPair} while futures
 * instruments map to {@link FuturesContract} carrying the provider symbol, contract type, and
 * expiry, never inferred from symbol text.
 */
public final class UtaAdapters {

  private UtaAdapters() {}

  /** Perpetual-swap prompt used for futures contracts without an expiry. */
  public static final String PERPETUAL_PROMPT = "PERP";

  /**
   * Maps a catalog instrument to an XChange instrument.
   *
   * @param tradeType authoritative trade type from the catalog response (SPOT or FUTURES)
   * @param instrument the provider instrument
   * @return CurrencyPair for spot; FuturesContract for futures
   */
  public static Instrument adaptInstrument(String tradeType, UtaInstrument instrument) {
    CurrencyPair pair = new CurrencyPair(instrument.getBaseCurrency(), instrument.getQuoteCurrency());
    if ("FUTURES".equalsIgnoreCase(tradeType)) {
      String prompt =
          instrument.getExpiryTime() != null
              ? new java.text.SimpleDateFormat("yyyyMMdd")
                  .format(new Date(instrument.getExpiryTime()))
              : PERPETUAL_PROMPT;
      return new FuturesContract(pair, prompt);
    }
    return pair;
  }

  /**
   * Reverses {@link #adaptInstrument}: returns the provider symbol for an instrument.
   *
   * @param instrument the XChange instrument
   * @param providerSymbols catalog-derived provider symbols, used to keep the exact provider
   *     identity (for example {@code XBTUSDTM}) when it cannot be re-derived
   */
  public static String adaptSymbol(Instrument instrument, Map<Instrument, String> providerSymbols) {
    String registered = providerSymbols.get(instrument);
    if (registered != null) {
      return registered;
    }
    if (instrument instanceof FuturesContract) {
      FuturesContract contract = (FuturesContract) instrument;
      return contract.getCurrencyPair().getBase().getCurrencyCode()
          + contract.getCurrencyPair().getCounter().getCurrencyCode()
          + "M";
    }
    return instrument.getBase().getCurrencyCode() + "-" + instrument.getCounter().getCurrencyCode();
  }

  public static InstrumentMetaData toInstrumentMetaData(UtaInstrument instrument) {
    return InstrumentMetaData.builder()
        .minimumAmount(instrument.getMinBaseOrderSize())
        .maximumAmount(instrument.getMaxBaseOrderSize())
        .counterMinimumAmount(instrument.getMinQuoteOrderSize())
        .counterMaximumAmount(instrument.getMaxQuoteOrderSize())
        .priceScale(instrument.getTickSize() == null ? 0 : instrument.getTickSize().scale())
        .priceStepSize(instrument.getTickSize())
        .volumeScale(
            instrument.getBaseOrderStep() == null ? 0 : instrument.getBaseOrderStep().scale())
        .amountStepSize(instrument.getBaseOrderStep())
        .tradingFeeCurrency(
            instrument.getFeeCurrency() == null
                ? null
                : Currency.getInstance(instrument.getFeeCurrency()))
        .marketOrderEnabled("1".equals(instrument.getTradingStatus()))
        .build();
  }

  public static Ticker adaptTicker(Instrument instrument, UtaTicker ticker) {
    return new Ticker.Builder()
        .instrument(instrument)
        .bid(ticker.getBestBidPrice())
        .bidSize(ticker.getBestBidSize())
        .ask(ticker.getBestAskPrice())
        .askSize(ticker.getBestAskSize())
        .last(ticker.getLastPrice())
        .high(ticker.getHigh())
        .low(ticker.getLow())
        .volume(ticker.getBaseVolume())
        .quoteVolume(ticker.getQuoteVolume())
        .open(ticker.getOpen())
        .percentageChange(
            ticker.getPriceChangePercent() == null
                ? null
                : ticker.getPriceChangePercent().movePointLeft(2))
        .build();
  }

  public static OrderBook adaptOrderBook(Instrument instrument, UtaOrderBook book) {
    Date timestamp = new Date(System.currentTimeMillis());
    List<LimitOrder> asks =
        book.getAsks().stream()
            .map(UtaAdapters::priceAndSize)
            .sorted(Ordering.natural().onResultOf(s -> s.price))
            .map(s -> limitOrder(instrument, OrderType.ASK, s))
            .collect(toCollection(LinkedList::new));
    List<LimitOrder> bids =
        book.getBids().stream()
            .map(UtaAdapters::priceAndSize)
            .sorted(Ordering.natural().onResultOf((PriceAndSize s) -> s.price).reversed())
            .map(s -> limitOrder(instrument, OrderType.BID, s))
            .collect(toCollection(LinkedList::new));
    return new OrderBook(timestamp, asks, bids, true);
  }

  private static LimitOrder limitOrder(Instrument instrument, OrderType type, PriceAndSize s) {
    return new LimitOrder.Builder(type, instrument)
        .limitPrice(s.price)
        .originalAmount(s.size)
        .build();
  }

  private static PriceAndSize priceAndSize(List<BigDecimal> row) {
    if (row == null || row.size() < 2) {
      throw new IllegalArgumentException("Invalid UTA order book row: " + row);
    }
    return new PriceAndSize(row.get(0), row.get(1));
  }

  private static final class PriceAndSize {
    final BigDecimal price;
    final BigDecimal size;

    PriceAndSize(BigDecimal price, BigDecimal size) {
      this.price = price;
      this.size = size;
    }
  }

  public static Trades adaptTrades(Instrument instrument, List<UtaTrade> trades) {
    return new Trades(
        trades.stream()
            .map(
                t ->
                    Trade.builder()
                        .instrument(instrument)
                        .originalAmount(t.getSize())
                        .price(t.getPrice())
                        .timestamp(new Date(nanosToMillis(t.getTs())))
                        .type("BUY".equalsIgnoreCase(t.getSide()) ? OrderType.BID : OrderType.ASK)
                        .id(t.getTradeId())
                        .build())
            .collect(Collectors.toList()),
        TradeSortType.SortByTimestamp);
  }

  public static Balance adaptBalance(UtaCurrencyAsset asset) {
    return new Balance(
        Currency.getInstance(asset.getCurrency()),
        asset.getBalance(),
        asset.getAvailable(),
        asset.getHold());
  }

  /** Converts a nanosecond timestamp to millis for XChange {@link Date} fields. */
  public static long nanosToMillis(Long nanos) {
    return nanos == null ? 0L : nanos / 1_000_000L;
  }

  /** Converts a millisecond timestamp to millis (provider already returns millis). */
  public static long millis(Long value) {
    return value == null ? 0L : value;
  }

  /** Maps a UTA order record to an XChange order. */
  public static org.knowm.xchange.dto.Order adaptOrder(UtaOrder order) {
    Instrument instrument = instrumentForSymbol(order.getSymbol());
    Order.OrderType type =
        "SELL".equalsIgnoreCase(order.getSide()) ? OrderType.ASK : OrderType.BID;
    Order.OrderStatus status = adaptStatus(order.getStatus());

    Order.Builder builder;
    if (order.getTriggerPrice() != null || order.getTriggerDirection() != null) {
      builder =
          new org.knowm.xchange.dto.trade.StopOrder.Builder(type, instrument)
              .stopPrice(order.getTriggerPrice())
              .limitPrice(
                  order.getPrice() != null && order.getPrice().signum() > 0
                      ? order.getPrice()
                      : null);
    } else {
      builder = new org.knowm.xchange.dto.trade.LimitOrder.Builder(type, instrument)
          .limitPrice(order.getPrice());
    }
    return builder
        .id(order.getOrderId())
        .userReference(order.getClientOid())
        .orderStatus(status)
        .originalAmount(order.getSize())
        .cumulativeAmount(order.getFilledSize())
        .averagePrice(order.getAvgPrice())
        .fee(order.getFee())
        .timestamp(new Date(nanosToMillis(order.getOrderTime())))
        .build();
  }

  private static Order.OrderStatus adaptStatus(Integer status) {
    if (status == null) {
      return Order.OrderStatus.UNKNOWN;
    }
    switch (status) {
      case 0:
      case 1:
        return Order.OrderStatus.PENDING_NEW;
      case 2:
        return Order.OrderStatus.NEW;
      case 3:
        return Order.OrderStatus.FILLED;
      case 4:
        return Order.OrderStatus.PARTIALLY_FILLED;
      case 5:
        return Order.OrderStatus.CANCELED;
      case 6:
        return Order.OrderStatus.PARTIALLY_CANCELED;
      default:
        return Order.OrderStatus.UNKNOWN;
    }
  }

  public static UserTrade adaptUserTrade(UtaExecution execution) {
    Instrument instrument = instrumentForSymbol(execution.getSymbol());
    return UserTrade.builder()
        .instrument(instrument)
        .id(execution.getTradeId())
        .orderId(execution.getOrderId())
        .originalAmount(execution.getSize())
        .price(execution.getPrice())
        .feeAmount(execution.getFee())
        .feeCurrency(
            execution.getFeeCurrency() == null
                ? null
                : Currency.getInstance(execution.getFeeCurrency()))
        .timestamp(new Date(nanosToMillis(execution.getExecutionTime())))
        .type("SELL".equalsIgnoreCase(execution.getSide()) ? OrderType.ASK : OrderType.BID)
        .build();
  }

  public static org.knowm.xchange.dto.account.OpenPosition adaptPosition(UtaPosition position) {
    Instrument instrument = instrumentForSymbol(position.getSymbol());
    return org.knowm.xchange.dto.account.OpenPosition.builder()
        .id(position.getId())
        .instrument(instrument)
        .type(
            position.getSize() != null && position.getSize().signum() < 0
                ? org.knowm.xchange.dto.account.OpenPosition.Type.SHORT
                : org.knowm.xchange.dto.account.OpenPosition.Type.LONG)
        .marginMode(
            "ISOLATED".equalsIgnoreCase(position.getMarginMode())
                ? org.knowm.xchange.dto.account.OpenPosition.MarginMode.ISOLATED
                : org.knowm.xchange.dto.account.OpenPosition.MarginMode.CROSS)
        .size(position.getSize().abs())
        .price(position.getEntryPrice())
        .liquidationPrice(position.getLiquidationPrice())
        .unRealisedPnl(position.getUnrealizedPnL())
        .createdAt(
            position.getCreationTime() == null
                ? null
                : java.time.Instant.ofEpochMilli(nanosToMillis(position.getCreationTime())))
        .build();
  }

  /**
   * Resolves an XChange instrument for a provider symbol.
   *
   * <p>Spot symbols ({@code BASE-QUOTE}) map to {@link CurrencyPair}; futures symbols use the
   * conventional {@code BASEQUOTEM} form mapped to a perpetual {@link FuturesContract}.
   */
  public static Instrument instrumentForSymbol(String symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("Provider symbol must not be null");
    }
    if (symbol.contains("-")) {
      String[] parts = symbol.split("-");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid UTA spot symbol: " + symbol);
      }
      return new CurrencyPair(parts[0], parts[1]);
    }
    if (symbol.endsWith("M")) {
      String base = symbol.substring(0, symbol.length() - 3);
      String quote = symbol.substring(symbol.length() - 3);
      return new FuturesContract(new CurrencyPair(base, quote), PERPETUAL_PROMPT);
    }
    throw new IllegalArgumentException("Unrecognized UTA symbol format: " + symbol);
  }
}
