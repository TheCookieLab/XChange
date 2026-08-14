package org.knowm.xchange.mexc.v3;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3Account;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3Balance;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3AggTrade;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3AvgPrice;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3BookTicker;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Depth;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3ExchangeInfo;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Kline;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3PriceLevel;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Symbol;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Ticker24h;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Trade;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3Order;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderStatus;

/** Converts MEXC Spot v3 DTOs to XChange domain objects. */
public final class MexcV3Adapters {

  /** MEXC Spot v3 symbol status meaning "online". */
  public static final String SYMBOL_STATUS_ONLINE = "1";

  private MexcV3Adapters() {}

  /**
   * Builds exchange metadata from {@code GET /api/v3/exchangeInfo}.
   *
   * <p>Only online, spot-tradable symbols are included. Precision and minimum-amount fields are
   * taken verbatim from the provider; a malformed payload (missing or non-numeric minimums) fails
   * loudly instead of producing guessed values.
   */
  public static ExchangeMetaData adaptExchangeInfo(MexcV3ExchangeInfo exchangeInfo) {
    Map<Instrument, InstrumentMetaData> instruments = new LinkedHashMap<>();
    Map<Currency, CurrencyMetaData> currencies = new LinkedHashMap<>();
    if (exchangeInfo.getSymbols() == null) {
      return new ExchangeMetaData(instruments, currencies, null, null, null);
    }
    for (MexcV3Symbol symbol : exchangeInfo.getSymbols()) {
      if (!SYMBOL_STATUS_ONLINE.equals(symbol.getStatus()) || !symbol.isSpotTradingAllowed()) {
        continue;
      }
      if (symbol.getBaseAsset() == null || symbol.getQuoteAsset() == null) {
        throw new IllegalArgumentException(
            "Malformed MEXC exchangeInfo symbol '" + symbol.getSymbol() + "': missing asset names");
      }
      CurrencyPair pair = new CurrencyPair(symbol.getBaseAsset(), symbol.getQuoteAsset());
      InstrumentMetaData metadata =
          InstrumentMetaData.builder()
              .priceScale(symbol.getQuoteAssetPrecision())
              .volumeScale(symbol.getBaseAssetPrecision())
              .minimumAmount(new BigDecimal(symbol.getBaseSizePrecision()))
              .counterMinimumAmount(new BigDecimal(symbol.getQuoteAmountPrecision()))
              .marketOrderEnabled(
                  symbol.getOrderTypes() != null
                      && symbol.getOrderTypes().contains("MARKET"))
              .build();
      instruments.put(pair, metadata);
      currencies.computeIfAbsent(
          pair.getBase(), c -> new CurrencyMetaData(symbol.getBaseAssetPrecision(), null));
      currencies.computeIfAbsent(
          pair.getCounter(), c -> new CurrencyMetaData(symbol.getQuoteAssetPrecision(), null));
    }
    return new ExchangeMetaData(instruments, currencies, null, null, null);
  }

  /** Builds an order book from a depth snapshot; levels are sorted ascending asks/descending bids. */
  public static OrderBook adaptOrderBook(MexcV3Depth depth, CurrencyPair pair) {
    Date timestamp = new Date();
    List<LimitOrder> asks = adaptLevels(depth.getAsks(), OrderType.ASK, pair, timestamp);
    List<LimitOrder> bids = adaptLevels(depth.getBids(), OrderType.BID, pair, timestamp);
    return new OrderBook(timestamp, asks, bids, true);
  }

  private static List<LimitOrder> adaptLevels(
      List<MexcV3PriceLevel> levels, OrderType type, CurrencyPair pair, Date timestamp) {
    List<LimitOrder> orders = new ArrayList<>();
    if (levels == null) {
      return orders;
    }
    for (MexcV3PriceLevel level : levels) {
      orders.add(
          new LimitOrder.Builder(type, pair)
              .timestamp(timestamp)
              .limitPrice(new BigDecimal(level.getPrice()))
              .originalAmount(new BigDecimal(level.getQuantity()))
              .build());
    }
    return orders;
  }

  /** Converts recent public trades; {@code isBuyerMaker} true means the aggressor sold. */
  public static Trades adaptTrades(List<MexcV3Trade> trades, CurrencyPair pair) {
    List<Trade> adapted = new ArrayList<>();
    if (trades != null) {
      for (MexcV3Trade trade : trades) {
        adapted.add(
            Trade.builder()
                .id(String.valueOf(trade.getId()))
                .instrument(pair)
                .originalAmount(new BigDecimal(trade.getQty()))
                .price(new BigDecimal(trade.getPrice()))
                .timestamp(new Date(trade.getTime()))
                .type(trade.isBuyerMaker() ? OrderType.ASK : OrderType.BID)
                .build());
      }
    }
    return new Trades(adapted, 0L, TradeSortType.SortByID);
  }

  /** Converts aggregated public trades; {@code m} true means the aggressor sold. */
  public static Trades adaptAggTrades(List<MexcV3AggTrade> trades, CurrencyPair pair) {
    List<Trade> adapted = new ArrayList<>();
    if (trades != null) {
      for (MexcV3AggTrade trade : trades) {
        adapted.add(
            Trade.builder()
                .id(String.valueOf(trade.getA()))
                .instrument(pair)
                .originalAmount(new BigDecimal(trade.getQ()))
                .price(new BigDecimal(trade.getP()))
                .timestamp(new Date(trade.getT()))
                .type(trade.isM() ? OrderType.ASK : OrderType.BID)
                .build());
      }
    }
    return new Trades(adapted, 0L, TradeSortType.SortByID);
  }

  /** Converts REST klines; a row is completed once its close time has passed. */
  public static List<CandleStick> adaptKlines(List<MexcV3Kline> klines, CurrencyPair pair) {
    List<CandleStick> adapted = new ArrayList<>();
    if (klines == null) {
      return adapted;
    }
    long now = System.currentTimeMillis();
    for (MexcV3Kline kline : klines) {
      adapted.add(
          new CandleStick(
              Instant.ofEpochMilli(kline.getOpenTime()),
              new BigDecimal(kline.getOpen()),
              new BigDecimal(kline.getClose()),
              new BigDecimal(kline.getHigh()),
              new BigDecimal(kline.getLow()),
              new BigDecimal(kline.getClose()),
              new BigDecimal(kline.getVolume()),
              new BigDecimal(kline.getQuoteAssetVolume()),
              null,
              null,
              null,
              null,
              null,
              kline.getCloseTime() <= now));
    }
    return adapted;
  }

  /** Converts a 24-hour rolling ticker. */
  public static Ticker adaptTicker24h(MexcV3Ticker24h ticker, CurrencyPair pair) {
    return new Ticker.Builder()
        .instrument(pair)
        .open(new BigDecimal(ticker.getOpenPrice()))
        .last(new BigDecimal(ticker.getLastPrice()))
        .bid(new BigDecimal(ticker.getBidPrice()))
        .bidSize(new BigDecimal(ticker.getBidQty()))
        .ask(new BigDecimal(ticker.getAskPrice()))
        .askSize(new BigDecimal(ticker.getAskQty()))
        .high(new BigDecimal(ticker.getHighPrice()))
        .low(new BigDecimal(ticker.getLowPrice()))
        .volume(new BigDecimal(ticker.getVolume()))
        .quoteVolume(
            ticker.getQuoteVolume() == null ? null : new BigDecimal(ticker.getQuoteVolume()))
        .percentageChange(new BigDecimal(ticker.getPriceChangePercent()))
        .timestamp(new Date(ticker.getCloseTime()))
        .build();
  }

  /** Converts a book ticker (best bid/ask) into a minimal ticker. */
  public static Ticker adaptBookTicker(MexcV3BookTicker ticker, CurrencyPair pair) {
    return new Ticker.Builder()
        .instrument(pair)
        .bid(new BigDecimal(ticker.getBidPrice()))
        .bidSize(new BigDecimal(ticker.getBidQty()))
        .ask(new BigDecimal(ticker.getAskPrice()))
        .askSize(new BigDecimal(ticker.getAskQty()))
        .timestamp(new Date())
        .build();
  }

  /** Converts a symbol price ticker into a minimal ticker carrying the last price. */
  public static Ticker adaptAvgPrice(MexcV3AvgPrice avgPrice, CurrencyPair pair) {
    return new Ticker.Builder()
        .instrument(pair)
        .last(new BigDecimal(avgPrice.getPrice()))
        .timestamp(new Date())
        .build();
  }

  /** Converts an account snapshot into a single spot wallet. */
  public static Wallet adaptWallet(MexcV3Account account) {
    Map<Currency, org.knowm.xchange.dto.account.Balance> balances = new LinkedHashMap<>();
    if (account.getBalances() != null) {
      for (MexcV3Balance balance : account.getBalances()) {
        balances.put(
            Currency.getInstance(balance.getAsset()),
            new org.knowm.xchange.dto.account.Balance(
                Currency.getInstance(balance.getAsset()),
                new BigDecimal(balance.getFree()).add(new BigDecimal(balance.getLocked())),
                new BigDecimal(balance.getFree()),
                new BigDecimal(balance.getLocked())));
      }
    }
    return new Wallet(
        "spot",
        null,
        balances.values(),
        java.util.EnumSet.of(Wallet.WalletFeature.TRADING),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  /** Maps a provider order status to the XChange {@code OrderStatus} enumeration. */
  public static org.knowm.xchange.dto.Order.OrderStatus adaptOrderStatus(
      MexcV3OrderStatus status) {
    if (status == null) {
      return org.knowm.xchange.dto.Order.OrderStatus.UNKNOWN;
    }
    switch (status) {
      case NEW:
        return org.knowm.xchange.dto.Order.OrderStatus.NEW;
      case FILLED:
        return org.knowm.xchange.dto.Order.OrderStatus.FILLED;
      case PARTIALLY_FILLED:
        return org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED;
      case CANCELED:
        return org.knowm.xchange.dto.Order.OrderStatus.CANCELED;
      case PARTIALLY_CANCELED:
        return org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_CANCELED;
      default:
        return org.knowm.xchange.dto.Order.OrderStatus.UNKNOWN;
    }
  }

  /** Converts an order query result into a limit order for {@code OrderBook} consumers. */
  public static LimitOrder adaptOrder(MexcV3Order order, CurrencyPair pair) {
    OrderType type =
        "SELL".equalsIgnoreCase(order.getSide().name()) ? OrderType.ASK : OrderType.BID;
    return new LimitOrder.Builder(type, pair)
        .id(order.getOrderId())
        .userReference(order.getClientOrderId())
        .timestamp(new Date(order.getTime()))
        .orderStatus(adaptOrderStatus(order.getStatus()))
        .limitPrice(
            order.getPrice() == null || order.getPrice().isEmpty()
                ? null
                : new BigDecimal(order.getPrice()))
        .originalAmount(new BigDecimal(order.getOrigQty()))
        .cumulativeAmount(
            order.getExecutedQty() == null || order.getExecutedQty().isEmpty()
                ? BigDecimal.ZERO
                : new BigDecimal(order.getExecutedQty()))
        .build();
  }
}
