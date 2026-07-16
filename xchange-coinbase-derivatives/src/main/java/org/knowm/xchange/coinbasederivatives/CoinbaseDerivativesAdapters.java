package org.knowm.xchange.coinbasederivatives;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.coinbasederivatives.dto.account.CoinbaseDerivativesAccountSummary;
import org.knowm.xchange.coinbasederivatives.dto.account.CoinbaseDerivativesPosition;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesOrderBook;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesTicker;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesTrade;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesOrder;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesUserTrade;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;

/** Wire-to-XChange adapters for Coinbase derivatives. */
public final class CoinbaseDerivativesAdapters {
  private static final Map<String, Instrument> SYMBOL_TO_INSTRUMENT = new ConcurrentHashMap<>();
  private static final Map<Instrument, String> INSTRUMENT_TO_SYMBOL = new ConcurrentHashMap<>();

  private CoinbaseDerivativesAdapters() {}

  /** Registers a provider-discovered instrument and returns its XChange representation. */
  public static Instrument registerInstrument(CoinbaseDerivativesInstrument providerInstrument) {
    Instrument instrument = toInstrument(providerInstrument);
    SYMBOL_TO_INSTRUMENT.put(providerInstrument.instrumentName(), instrument);
    INSTRUMENT_TO_SYMBOL.put(instrument, providerInstrument.instrumentName());
    return instrument;
  }

  /** Maps authoritative provider metadata to an XChange futures contract. */
  public static Instrument toInstrument(CoinbaseDerivativesInstrument providerInstrument) {
    if (providerInstrument == null
        || providerInstrument.instrumentName() == null
        || providerInstrument.baseCurrency() == null
        || providerInstrument.counterCurrency() == null) {
      throw new ExchangeException("Incomplete Coinbase derivatives instrument metadata");
    }
    String prompt = prompt(providerInstrument.instrumentName());
    return new FuturesContract(
        new CurrencyPair(providerInstrument.baseCurrency(), providerInstrument.counterCurrency()),
        prompt);
  }

  /** Resolves a native name observed in a provider response. */
  public static Instrument toInstrument(String nativeName) {
    Instrument known = SYMBOL_TO_INSTRUMENT.get(nativeName);
    if (known != null) {
      return known;
    }
    int separator = nativeName.indexOf('-');
    String pair = separator < 0 ? nativeName : nativeName.substring(0, separator);
    String[] currencies = pair.split("_");
    if (currencies.length != 2) {
      throw new ExchangeException("Unknown Coinbase derivatives instrument: " + nativeName);
    }
    return new FuturesContract(new CurrencyPair(currencies[0], currencies[1]), prompt(nativeName));
  }

  /** Returns the provider name only for an instrument discovered from provider metadata. */
  public static String toNativeName(Instrument instrument) {
    String nativeName = INSTRUMENT_TO_SYMBOL.get(instrument);
    if (nativeName == null) {
      throw new ExchangeException(
          "Instrument was not discovered from Coinbase derivatives metadata: " + instrument);
    }
    return nativeName;
  }

  public static InstrumentMetaData adaptMetadata(CoinbaseDerivativesInstrument instrument) {
    BigDecimal tickSize = instrument.tickSize();
    BigDecimal minimum = instrument.minimumTradeAmount();
    return InstrumentMetaData.builder()
        .tradingFee(instrument.takerCommission())
        .minimumAmount(minimum)
        .volumeScale(minimum == null ? null : Math.max(0, minimum.scale()))
        .priceScale(tickSize == null ? null : Math.max(0, tickSize.scale()))
        .priceStepSize(tickSize)
        .contractValue(instrument.contractSize())
        .marketOrderEnabled(true)
        .build();
  }

  public static Ticker adaptTicker(CoinbaseDerivativesTicker ticker) {
    CoinbaseDerivativesTicker.Stats stats = ticker.stats();
    return new Ticker.Builder()
        .instrument(toInstrument(ticker.instrumentName()))
        .timestamp(toDate(ticker.timestamp()))
        .last(ticker.lastPrice())
        .bid(ticker.bestBidPrice())
        .ask(ticker.bestAskPrice())
        .bidSize(ticker.bestBidAmount())
        .askSize(ticker.bestAskAmount())
        .high(stats == null ? null : stats.high())
        .low(stats == null ? null : stats.low())
        .volume(stats == null ? null : stats.volume())
        .build();
  }

  public static OrderBook adaptOrderBook(CoinbaseDerivativesOrderBook book) {
    Instrument instrument = toInstrument(book.instrumentName());
    return new OrderBook(
        toDate(book.timestamp()),
        adaptLevels(book.asks(), Order.OrderType.ASK, instrument),
        adaptLevels(book.bids(), Order.OrderType.BID, instrument));
  }

  private static List<LimitOrder> adaptLevels(
      List<List<BigDecimal>> levels, Order.OrderType side, Instrument instrument) {
    if (levels == null) {
      return List.of();
    }
    return levels.stream()
        .filter(level -> level != null && level.size() >= 2)
        .map(
            level ->
                new LimitOrder(
                    side,
                    level.get(level.size() - 1),
                    instrument,
                    null,
                    null,
                    level.get(level.size() - 2)))
        .toList();
  }

  public static Trade adaptTrade(CoinbaseDerivativesTrade trade) {
    return Trade.builder()
        .type(toSide(trade.direction()))
        .originalAmount(trade.amount())
        .instrument(toInstrument(trade.instrumentName()))
        .price(trade.price())
        .timestamp(toDate(trade.timestamp()))
        .id(trade.tradeId())
        .build();
  }

  public static Balance adaptBalance(CoinbaseDerivativesAccountSummary account) {
    return new Balance(
        Currency.getInstance(account.currency()), account.balance(), account.availableFunds());
  }

  public static OpenPosition adaptPosition(CoinbaseDerivativesPosition position) {
    BigDecimal size = position.sizeCurrency() == null ? position.size() : position.sizeCurrency();
    OpenPosition.Type type =
        "sell".equalsIgnoreCase(position.direction()) || (size != null && size.signum() < 0)
            ? OpenPosition.Type.SHORT
            : OpenPosition.Type.LONG;
    return OpenPosition.builder()
        .instrument(toInstrument(position.instrumentName()))
        .type(type)
        .size(size)
        .marginMode(
            position.marginModel() != null
                    && position.marginModel().toLowerCase(Locale.ROOT).contains("segregated")
                ? OpenPosition.MarginMode.ISOLATED
                : OpenPosition.MarginMode.CROSS)
        .price(position.averagePrice())
        .liquidationPrice(position.estimatedLiquidationPrice())
        .unRealisedPnl(position.unrealizedPnl())
        .createdAt(
            position.creationTimestamp() == null
                ? null
                : Instant.ofEpochMilli(position.creationTimestamp()))
        .build();
  }

  public static Order adaptOrder(CoinbaseDerivativesOrder order) {
    Instrument instrument = toInstrument(order.instrumentName());
    Order.Builder builder;
    String type = order.orderType() == null ? "" : order.orderType().toLowerCase(Locale.ROOT);
    if (type.contains("stop") || type.contains("trigger")) {
      builder =
          new StopOrder.Builder(toSide(order.direction()), instrument)
              .stopPrice(order.triggerPrice())
              .limitPrice(type.contains("limit") ? order.price() : null);
    } else if (type.contains("market")) {
      builder = new MarketOrder.Builder(toSide(order.direction()), instrument);
    } else {
      builder =
          new LimitOrder.Builder(toSide(order.direction()), instrument).limitPrice(order.price());
    }
    return builder
        .id(order.orderId())
        .originalAmount(firstNonNull(order.amount(), order.contracts()))
        .cumulativeAmount(order.filledAmount())
        .averagePrice(order.averagePrice())
        .fee(order.commission())
        .timestamp(order.creationTimestamp() == null ? null : toDate(order.creationTimestamp()))
        .orderStatus(toStatus(order.orderState()))
        .userReference(order.label())
        .build();
  }

  public static UserTrade adaptUserTrade(CoinbaseDerivativesUserTrade trade) {
    return UserTrade.builder()
        .type(toSide(trade.direction()))
        .originalAmount(firstNonNull(trade.amount(), trade.contracts()))
        .instrument(toInstrument(trade.instrumentName()))
        .price(trade.price())
        .timestamp(toDate(trade.timestamp()))
        .id(trade.tradeId())
        .orderId(trade.orderId())
        .feeAmount(trade.fee())
        .feeCurrency(trade.feeCurrency() == null ? null : Currency.getInstance(trade.feeCurrency()))
        .orderUserReference(trade.label())
        .build();
  }

  private static String prompt(String nativeName) {
    int separator = nativeName.indexOf('-');
    return separator < 0 ? "PERPETUAL" : nativeName.substring(separator + 1);
  }

  private static Date toDate(long timestamp) {
    return new Date(timestamp);
  }

  private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
    return first == null ? second : first;
  }

  private static Order.OrderType toSide(String direction) {
    return "sell".equalsIgnoreCase(direction) ? Order.OrderType.ASK : Order.OrderType.BID;
  }

  private static Order.OrderStatus toStatus(String state) {
    if (state == null) {
      return Order.OrderStatus.UNKNOWN;
    }
    return switch (state.toLowerCase(Locale.ROOT)) {
      case "open", "untriggered" -> Order.OrderStatus.OPEN;
      case "filled" -> Order.OrderStatus.FILLED;
      case "rejected" -> Order.OrderStatus.REJECTED;
      case "cancelled", "canceled" -> Order.OrderStatus.CANCELED;
      default -> Order.OrderStatus.UNKNOWN;
    };
  }
}
