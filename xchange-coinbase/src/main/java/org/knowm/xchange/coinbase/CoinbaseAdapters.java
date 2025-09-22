package org.knowm.xchange.coinbase;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.knowm.xchange.coinbase.dto.account.CoinbaseUser;
import org.knowm.xchange.coinbase.dto.marketdata.CoinbaseHistoricalSpotPrice;
import org.knowm.xchange.coinbase.dto.marketdata.CoinbaseMoney;
import org.knowm.xchange.coinbase.dto.marketdata.CoinbasePrice;
import org.knowm.xchange.coinbase.dto.marketdata.CoinbaseSpotPriceHistory;
import org.knowm.xchange.coinbase.dto.trade.CoinbaseTransfer;
import org.knowm.xchange.coinbase.dto.trade.CoinbaseTransferType;
import org.knowm.xchange.coinbase.dto.trade.CoinbaseTransfers;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseBuySell;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBookEntry;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseMarketTrade;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandle;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Ticker.Builder;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.instrument.Instrument;

/**
 * jamespedwards42
 */
public final class CoinbaseAdapters {

  private static final int TWENTY_FOUR_HOURS_IN_MILLIS = 1000 * 60 * 60 * 24;
  private static final int PRICE_SCALE = 10;

  private CoinbaseAdapters() {
  }

  public static AccountInfo adaptAccountInfo(CoinbaseUser user) {

    final String username = user.getEmail();
    final CoinbaseMoney money = user.getBalance();
    final Balance balance = new Balance(Currency.getInstance(money.getCurrency()),
        money.getAmount());

    final AccountInfo accountInfoTemporaryName = new AccountInfo(username,
        Wallet.Builder.from(Arrays.asList(balance)).build());
    return accountInfoTemporaryName;
  }

  public static UserTrades adaptTrades(List<CoinbaseBuySell> transactions, OrderType orderType) {
    final List<UserTrade> trades = new ArrayList<>();

    for (CoinbaseBuySell transaction : transactions) {
      trades.add(adaptTrade(transaction, orderType));
    }

    return new UserTrades(trades, TradeSortType.SortByTimestamp);
  }

  private static UserTrade adaptTrade(CoinbaseBuySell transaction, OrderType orderType) {
    return UserTrade.builder().type(orderType).originalAmount(transaction.getAmount().getAmount())
        .currencyPair(new CurrencyPair(transaction.getAmount().getCurrency(),
            transaction.getTotal().getCurrency())).price(transaction.getSubTotal().getAmount()
            .divide(transaction.getAmount().getAmount(), PRICE_SCALE, RoundingMode.HALF_UP))
        .timestamp(Date.from(transaction.getCreatedAt().toInstant())).id(transaction.getId())
        .orderId(transaction.getTransaction().getId()).feeAmount(transaction.getFee().getAmount())
        .feeCurrency(Currency.getInstance(transaction.getFee().getCurrency())).build();
  }

  public static UserTrades adaptTrades(CoinbaseTransfers transfers) {

    final List<UserTrade> trades = new ArrayList<>();
    for (CoinbaseTransfer transfer : transfers.getTransfers()) {
      trades.add(adaptTrade(transfer));
    }

    return new UserTrades(trades, TradeSortType.SortByTimestamp);
  }

  public static UserTrade adaptTrade(CoinbaseTransfer transfer) {

    final OrderType orderType = adaptOrderType(transfer.getType());
    final CoinbaseMoney btcAmount = transfer.getBtcAmount();
    final BigDecimal originalAmount = btcAmount.getAmount();
    final String tradableIdentifier = btcAmount.getCurrency();
    final CoinbaseMoney subTotal = transfer.getSubtotal();
    final String transactionCurrency = subTotal.getCurrency();
    final BigDecimal price = subTotal.getAmount().divide(originalAmount, RoundingMode.HALF_EVEN);
    final Date timestamp = transfer.getCreatedAt();
    final String id = transfer.getTransactionId();
    final String transferId = transfer.getId();
    final BigDecimal feeAmount = transfer.getCoinbaseFee().getAmount();
    final String feeCurrency = transfer.getCoinbaseFee().getCurrency();

    return UserTrade.builder().type(orderType).originalAmount(originalAmount)
        .currencyPair(new CurrencyPair(tradableIdentifier, transactionCurrency)).price(price)
        .timestamp(timestamp).id(id).orderId(transferId).feeAmount(feeAmount)
        .feeCurrency(Currency.getInstance(feeCurrency)).build();
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
    return new Trade.Builder().id(marketTrade.getTradeId())
        .instrument(new CurrencyPair(marketTrade.getProductId())).price(marketTrade.getPrice())
        .originalAmount(marketTrade.getSize()).timestamp(
            Date.from(DateTimeFormatter.ISO_INSTANT.parse(marketTrade.getTime(), Instant::from)))
        .type(adaptOrderType(marketTrade.getSide())).build();
  }

  public static OrderType adaptOrderType(CoinbaseTransferType transferType) {
    return adaptOrderType(transferType.name());
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
      builder = builder.percentageChange(
              product.getPricePercentageChange24H().round(new MathContext(2, RoundingMode.HALF_EVEN)))
          .volume(product.getVolume24H()).quoteVolume(product.getApproximateQuoteVolume24H());
    }

    if (priceBook != null && !priceBook.getAsks().isEmpty() && !priceBook.getBids().isEmpty()) {
      builder = builder.ask(priceBook.getAsks().isEmpty() ? null : priceBook.getAsks().get(0).getPrice())
          .askSize(priceBook.getAsks().isEmpty() ? null : priceBook.getAsks().get(0).getSize())
          .bid(priceBook.getBids().isEmpty() ? null : priceBook.getBids().get(0).getPrice())
          .bidSize(priceBook.getBids().isEmpty() ? null : priceBook.getBids().get(0).getSize())
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

  public static Ticker adaptTicker(CurrencyPair currencyPair, final CoinbasePrice buyPrice,
      final CoinbasePrice sellPrice, final CoinbaseMoney spotRate,
      final CoinbaseSpotPriceHistory coinbaseSpotPriceHistory) {

    final Ticker.Builder tickerBuilder = new Ticker.Builder().currencyPair(currencyPair)
        .ask(buyPrice.getSubTotal().getAmount()).bid(sellPrice.getSubTotal().getAmount())
        .last(spotRate.getAmount());

    // Get the 24 hour high and low spot price if the history is provided.
    if (coinbaseSpotPriceHistory != null) {
      BigDecimal observedHigh = spotRate.getAmount();
      BigDecimal observedLow = spotRate.getAmount();
      Date twentyFourHoursAgo = null;
      // The spot price history list is sorted in descending order by timestamp when deserialized.
      for (CoinbaseHistoricalSpotPrice historicalSpotPrice : coinbaseSpotPriceHistory.getSpotPriceHistory()) {

        if (twentyFourHoursAgo == null) {
          twentyFourHoursAgo = new Date(
              historicalSpotPrice.getTimestamp().getTime() - TWENTY_FOUR_HOURS_IN_MILLIS);
        } else if (historicalSpotPrice.getTimestamp().before(twentyFourHoursAgo)) {
          break;
        }

        final BigDecimal spotPriceAmount = historicalSpotPrice.getSpotRate();
        if (spotPriceAmount.compareTo(observedLow) < 0) {
          observedLow = spotPriceAmount;
        } else if (spotPriceAmount.compareTo(observedHigh) > 0) {
          observedHigh = spotPriceAmount;
        }
      }
      tickerBuilder.high(observedHigh).low(observedLow);
    }

    return tickerBuilder.build();
  }
}
