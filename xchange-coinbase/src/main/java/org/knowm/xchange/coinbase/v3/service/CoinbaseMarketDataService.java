package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseProductPriceBookResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseMarketDataService extends CoinbaseMarketDataServiceRaw implements
    MarketDataService {

  public CoinbaseMarketDataService(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseMarketDataService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseMarketDataService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }

  public List<CoinbasePriceBook> getBestBidAsk(Currency base, Currency counter) throws IOException {
    CurrencyPair currencyPair = new CurrencyPair(base, counter);

    return this.getBestBidAsk(currencyPair);
  }

  public List<CoinbasePriceBook> getBestBidAsk(CurrencyPair currencyPair) throws IOException {
    return this.getBestBidAsk(CoinbaseAdapters.adaptProductId(currencyPair)).getPriceBooks();
  }

  @Override
  public Ticker getTicker(Instrument instrument, final Object... args) throws IOException {
    String productId = CoinbaseAdapters.adaptProductId(instrument);
    CoinbaseProductResponse product = this.getProduct(productId);
    List<CoinbasePriceBook> priceBooks = this.getBestBidAsk(productId).getPriceBooks();
    CoinbaseProductCandlesResponse candle = this.getProductCandles(productId, "ONE_DAY", 1, null, null);

    return CoinbaseAdapters.adaptTicker(product, candle, priceBooks.get(0));
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, final Object... args) throws IOException {
    Integer limit = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : null;
    Double aggregationPriceIncrement = args.length > 1 && args[1] instanceof Double ? (Double) args[1] : null;

    CoinbaseProductPriceBookResponse response = this.getProductBook(CoinbaseAdapters.adaptProductId(instrument), limit, aggregationPriceIncrement);

    return CoinbaseAdapters.adaptOrderBook(response.getPriceBook());
  }

  /**
   * Retrieves market trades for the specified instrument with optional parameters.
   *
   * @param instrument The financial instrument (e.g., currency pair) for which trades are
   *                   requested.
   * @param args       Optional parameters in the following order: <br> 1. {@code Integer} limit:
   *                   Maximum number of trades to retrieve. <br> 2. {@code String} start: Start
   *                   time for the trade history (ISO 8601 format). <br> 3. {@code String} end: End
   *                   time for the trade history (ISO 8601 format). <br>
   * @return A {@link Trades} object containing a list of trades sorted by ID or timestamp, along
   * with metadata such as the last trade ID and next page cursor.
   * @throws IOException If there is an error communicating with the exchange.
   */
  @Override
  public Trades getTrades(Instrument instrument, final Object... args) throws IOException {
    Integer limit = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : null;
    String start = args.length > 1 && args[1] instanceof String ? (String) args[1] : null;
    String end = args.length > 2 && args[2] instanceof String ? (String) args[2] : null;

    CoinbaseProductMarketTradesResponse response = this.getMarketTrades(
        CoinbaseAdapters.adaptProductId(instrument), limit, start, end);

    List<Trade> trades = response.getMarketTrades().stream()
        .map(CoinbaseAdapters::adaptTrade)
        .collect(Collectors.toList());

    return new Trades(trades);
  }

  /**
   * Fetches candlestick data for a given currency pair and parameters.
   *
   * @param currencyPair The currency pair for which data is requested.
   * @param params       Additional parameters for candlestick data (e.g., period, start/end time,
   *                     limit).
   * @return A {@link CandleStickData} object containing the candlestick data.
   * @throws IOException              If there is an error communicating with the exchange.
   * @throws IllegalArgumentException If the granularity is invalid or the limit exceeds API
   *                                  constraints.
   */
  @Override
  public CandleStickData getCandleStickData(CurrencyPair currencyPair, CandleStickDataParams params)
      throws IOException {

    String productId = CoinbaseAdapters.adaptProductId(currencyPair);
    String granularity = null;
    String start = null;
    String end = null;
    int limit = 350;

    if (params instanceof DefaultCandleStickParam) {
      DefaultCandleStickParam defaultParams = (DefaultCandleStickParam) params;
      granularity = CoinbaseAdapters.adaptProductCandleGranularity(defaultParams.getPeriodInSecs());
      if (granularity == null) {
        throw new IllegalArgumentException("Invalid granularity for Coinbase Product Candles API");
      }

      if (defaultParams.getStartDate() != null) {
        start = Long.toString(defaultParams.getStartDate().toInstant().getEpochSecond());
      }

      if (defaultParams.getEndDate() != null) {
        end = Long.toString(defaultParams.getEndDate().toInstant().getEpochSecond());
      }

      if (params instanceof DefaultCandleStickParamWithLimit) {
        DefaultCandleStickParamWithLimit paramsWithLimit = (DefaultCandleStickParamWithLimit) params;
        limit = paramsWithLimit.getLimit();
      }
    }

    CoinbaseProductCandlesResponse response = this.getProductCandles(productId, granularity, limit,
        start, end);

    List<CandleStick> candleSticks = response.getCandles().stream()
        .map(CoinbaseAdapters::adaptProductCandle)
        .collect(Collectors.toList());

    return new CandleStickData(currencyPair, candleSticks);
  }
}
