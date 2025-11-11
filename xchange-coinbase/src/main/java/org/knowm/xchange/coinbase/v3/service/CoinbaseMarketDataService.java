package org.knowm.xchange.coinbase.v3.service;

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
import org.knowm.xchange.dto.marketdata.*;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
import si.mazi.rescu.ParamsDigest;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Market data service implementation for Coinbase Advanced Trade (v3) API.
 * <p>
 * This service provides access to public market data including tickers, order books, trades,
 * and candlestick data. It extends {@link CoinbaseMarketDataServiceRaw} to provide high-level
 * XChange DTOs mapped from Coinbase-specific responses.
 * </p>
 * <p>
 * All methods in this service map Coinbase API responses to standard XChange market data objects
 * such as {@link Ticker}, {@link OrderBook}, {@link Trades}, and {@link CandleStickData}.
 * </p>
 */
public class CoinbaseMarketDataService extends CoinbaseMarketDataServiceRaw implements MarketDataService {

    /**
     * Constructs a new market data service using the exchange's default configuration.
     *
     * @param exchange The exchange instance containing API credentials and configuration.
     */
    public CoinbaseMarketDataService(Exchange exchange) {
        super(exchange);
    }

    /**
     * Constructs a new market data service with a custom authenticated API client.
     *
     * @param exchange              The exchange instance containing API credentials and configuration.
     * @param coinbaseAdvancedTrade The authenticated Coinbase API client for making requests.
     */
    public CoinbaseMarketDataService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
        super(exchange, coinbaseAdvancedTrade);
    }

    /**
     * Constructs a new market data service with a custom authenticated API client and token creator.
     *
     * @param exchange              The exchange instance containing API credentials and configuration.
     * @param coinbaseAdvancedTrade The authenticated Coinbase API client for making requests.
     * @param authTokenCreator      The parameter digest for creating authentication tokens.
     */
    public CoinbaseMarketDataService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade, ParamsDigest authTokenCreator) {
        super(exchange, coinbaseAdvancedTrade, authTokenCreator);
    }

    /**
     * Retrieves the best bid and ask price book entries for a currency pair specified by base and counter currencies.
     *
     * @param base    The base currency (e.g., BTC).
     * @param counter The counter currency (e.g., USD).
     * @return A list of {@link CoinbasePriceBook} objects containing bid and ask entries for the
     *         requested currency pair. Each entry includes price levels, quantities, and timestamps.
     * @throws IOException If there is an error communicating with the Coinbase API.
     */
    public List<CoinbasePriceBook> getBestBidAsk(Currency base, Currency counter) throws IOException {
        CurrencyPair currencyPair = new CurrencyPair(base, counter);

        return this.getBestBidAsk(currencyPair);
    }

    /**
     * Retrieves the best bid and ask price book entries for a currency pair.
     *
     * @param currencyPair The currency pair (e.g., BTC-USD) for which to fetch bid/ask data.
     * @return A list of {@link CoinbasePriceBook} objects containing bid and ask entries for the
     *         requested currency pair. Each entry includes price levels, quantities, and timestamps.
     * @throws IOException If there is an error communicating with the Coinbase API.
     */
    public List<CoinbasePriceBook> getBestBidAsk(CurrencyPair currencyPair) throws IOException {
        return this.getBestBidAsk(CoinbaseAdapters.adaptProductId(currencyPair)).getPriceBooks();
    }

    /**
     * Retrieves ticker data for the specified instrument.
     * <p>
     * The ticker includes current market information such as last price, bid/ask prices, 24-hour
     * high/low, volume, and price change percentage. This method aggregates data from multiple
     * Coinbase API endpoints (product details, best bid/ask, and daily candles) to build a
     * comprehensive ticker.
     * </p>
     *
     * @param instrument The financial instrument (e.g., currency pair) for which ticker data is requested.
     * @param args       Optional arguments (currently unused).
     * @return A {@link Ticker} object containing current market data for the instrument.
     * @throws IOException If there is an error communicating with the Coinbase API.
     */
    @Override
    public Ticker getTicker(Instrument instrument, final Object... args) throws IOException {
        String productId = CoinbaseAdapters.adaptProductId(instrument);
        CoinbaseProductResponse product = this.getProduct(productId);
        List<CoinbasePriceBook> priceBooks = this.getBestBidAsk(productId).getPriceBooks();
        CoinbaseProductCandlesResponse candle = this.getProductCandles(productId, "ONE_DAY", 1, null, null);

        CoinbasePriceBook priceBook = priceBooks.isEmpty() ? null : priceBooks.get(0);
        return CoinbaseAdapters.adaptTicker(product, candle, priceBook);
    }

    /**
     * Retrieves ticker data for the specified currency pair.
     * <p>
     * This is a convenience method that delegates to {@link #getTicker(Instrument, Object...)}.
     * </p>
     *
     * @param currencyPair The currency pair (e.g., BTC-USD) for which ticker data is requested.
     * @param args         Optional arguments (currently unused).
     * @return A {@link Ticker} object containing current market data for the currency pair.
     * @throws IOException If there is an error communicating with the Coinbase API.
     */
    @Override
    public Ticker getTicker(CurrencyPair currencyPair, final Object... args) throws IOException {
        return this.getTicker((Instrument) currencyPair, args);
    }

    /**
     * Retrieves the order book for the specified currency pair.
     * <p>
     * This is a convenience method that delegates to {@link #getOrderBook(Instrument, Object...)}.
     * </p>
     *
     * @param currencyPair The currency pair (e.g., BTC-USD) for which the order book is requested.
     * @param args         Optional parameters in the following order: <br>
     *                     1. {@code Integer} limit: Maximum number of price levels to retrieve. <br>
     *                     2. {@code Double} aggregationPriceIncrement: Price increment for aggregating order book data.
     * @return An {@link OrderBook} object containing bid and ask orders for the currency pair.
     * @throws IOException If there is an error communicating with the Coinbase API.
     */
    @Override
    public OrderBook getOrderBook(CurrencyPair currencyPair, final Object... args) throws IOException {
        return this.getOrderBook((Instrument) currencyPair, args);
    }

    /**
     * Retrieves the order book for the specified instrument with optional parameters.
     * <p>
     * The order book contains current bid and ask orders at various price levels, allowing
     * analysis of market depth and liquidity.
     * </p>
     *
     * @param instrument The financial instrument (e.g., currency pair) for which the order book is requested.
     * @param args       Optional parameters in the following order: <br>
     *                   1. {@code Integer} limit: Maximum number of price levels to retrieve. If null,
     *                   the API's default limit is used. <br>
     *                   2. {@code Double} aggregationPriceIncrement: Price increment interval for aggregating
     *                   order book data. Must be a positive value. If null, the API's default aggregation is applied.
     * @return An {@link OrderBook} object containing bid and ask orders for the instrument, including
     *         price levels, quantities, and timestamps.
     * @throws IOException If there is an error communicating with the Coinbase API.
     */
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

        CoinbaseProductMarketTradesResponse response = this.getMarketTrades(CoinbaseAdapters.adaptProductId(instrument), limit, start, end);

        List<Trade> trades = response.getMarketTrades().stream().map(CoinbaseAdapters::adaptTrade).collect(Collectors.toList());

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
    public CandleStickData getCandleStickData(CurrencyPair currencyPair, CandleStickDataParams params) throws IOException {

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

        CoinbaseProductCandlesResponse response = this.getProductCandles(productId, granularity, limit, start, end);

        List<CandleStick> candleSticks = response.getCandles().stream().map(CoinbaseAdapters::adaptProductCandle).collect(Collectors.toList());

        return new CandleStickData(currencyPair, candleSticks);
    }
}
