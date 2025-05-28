package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseMarketTrade;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
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
    throw new NotAvailableFromExchangeException();
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, final Object... args) {
    throw new NotAvailableFromExchangeException();
  }

  @Override
  public Trades getTrades(Instrument instrument, final Object... args) throws IOException {
    Integer limit = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : null;
    String start = args.length > 1 && args[1] instanceof String ? (String) args[1] : null;
    String end = args.length > 2 && args[2] instanceof String ? (String) args[2] : null;

    CoinbaseProductMarketTradesResponse response = this.getMarketTrades(
        CoinbaseAdapters.adaptProductId(instrument), limit, start, end);

    List<Trade> trades = new ArrayList<>();
    for (CoinbaseMarketTrade marketTrade : response.getMarketTrades()) {
      Trade trade = CoinbaseAdapters.adaptTrade(marketTrade);
      trades.add(trade);
    }

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
        throw new IllegalArgumentException("Invalid granularity for Coinbase API");
      }

      if (defaultParams.getStartDate() != null) {
        start = Long.toString(defaultParams.getStartDate().toInstant().getEpochSecond());
      }

      if (defaultParams.getEndDate() != null) {
        end = Long.toString(defaultParams.getEndDate().toInstant().getEpochSecond());
      }

      if (params instanceof DefaultCandleStickParamWithLimit) {
        DefaultCandleStickParamWithLimit paramsWithLimit = (DefaultCandleStickParamWithLimit) params;
        limit = Math.min(paramsWithLimit.getLimit(), 350);
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
