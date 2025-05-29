package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseBestBidAsksResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseProductPriceBookResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseMarketTrade;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandle;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import si.mazi.rescu.ParamsDigest;

class CoinbaseMarketDataServiceRaw extends CoinbaseBaseService {

  public CoinbaseMarketDataServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseMarketDataServiceRaw(Exchange exchange,
      CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseMarketDataServiceRaw(Exchange exchange,
      CoinbaseAuthenticated coinbaseAdvancedTrade, ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }

  public CoinbaseProductResponse getProduct(String productId) throws IOException {
    return coinbaseAdvancedTrade.getProduct(authTokenCreator, productId, false);
  }

  /**
   * Retrieves the best bid and ask price book entries for a specified product. This method
   * authenticates the request using the stored API credentials.
   *
   * @param productId The product identifier (e.g., "BTC-USD") for which to fetch bid/ask data. If
   *                  null is passed, then retrieve the best bid/ask data for ALL products
   * @return A list of {@link CoinbasePriceBook} objects containing bid and ask entries for the
   * requested product. Each entry includes price levels, quantities, and timestamps.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseBestBidAsksResponse getBestBidAsk(String productId) throws IOException {
    return coinbaseAdvancedTrade.getBestBidAsk(authTokenCreator, productId);
  }

  /**
   * Retrieves market trades for a specified product within a given time range and limit. This
   * method authenticates the request using the stored API credentials.
   *
   * @param productId The product identifier (e.g., "BTC-USD") for which to fetch market trades.
   *                  Must not be null.
   * @param limit     The maximum number of trades to retrieve. If null, the default limit enforced
   *                  by the API is used.
   * @param start     The start time for the trade data window in ISO 8601 format (e.g.,
   *                  "2023-10-01T00:00:00Z"). If null, data starts from the earliest available.
   * @param end       The end time for the trade data window in ISO 8601 format. If null, data ends
   *                  at the latest available timestamp.
   * @return A {@link CoinbaseProductMarketTradesResponse} containing: - A list of
   * {@link CoinbaseMarketTrade} objects representing individual trades, - The current best bid
   * price for the product, - The current best ask price for the product.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseProductMarketTradesResponse getMarketTrades(String productId, Integer limit,
      String start, String end) throws IOException {
    Objects.requireNonNull(productId, "productId cannot be null");

    return coinbaseAdvancedTrade.getMarketTrades(authTokenCreator, productId, limit, start, end);
  }

  /**
   * Retrieves candlestick data for a specified product within a given time range, granularity, and
   * limit. This method authenticates the request using the stored API credentials, and has the
   * benefit of being "real-time" as opposed to the public API version which by default uses a 1
   * minute cache <a
   * href="https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getcandles">https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getcandles</a>
   *
   * @param productId   The product identifier (e.g., "BTC-USD") for which to fetch candlestick
   *                    data. Must not be null.
   * @param granularity The timeframe each candle represents: ["ONE_MINUTE", "FIVE_MINUTE",
   *                    "FIFTEEN_MINUTE", "THIRTY_MINUTE", "ONE_HOUR", "TWO_HOUR", "SIX_HOUR",
   *                    "ONE_DAY"]
   * @param limit       The number of candle buckets to be returned. By default, returns 350 (max
   *                    350).
   * @param start       The start time for the candlestick data window in ISO 8601 format (e.g.,
   *                    "2023-10-01T00:00:00Z"). If null, data starts from the earliest available
   *                    timestamp.
   * @param end         The end time for the candlestick data window in ISO 8601 format. If null,
   *                    data ends at the latest available timestamp.
   * @return A {@link CoinbaseProductCandlesResponse} containing a list of
   * {@link CoinbaseProductCandle} objects. Each candle represents aggregated price and volume data
   * for the specified time interval, including open, high, low, close, and volume values.
   */
  public CoinbaseProductCandlesResponse getProductCandles(String productId, String granularity,
      Integer limit, String start, String end) throws IOException {
    Objects.requireNonNull(productId, "productId cannot be null");

    return coinbaseAdvancedTrade.getProductCandles(authTokenCreator, productId, start, end,
        granularity, limit);
  }

  /**
   * Retrieves the price book data for a specified product, including bid/ask levels, mid-market price,
   * and spread metrics. This method authenticates the request using the stored API credentials.
   *
   * @param productId The product identifier (e.g., "BTC-USD") for which to fetch price book data. Must not be null.
   * @param limit The maximum number of price levels to retrieve. If null, the API's default limit is used.
   * @param aggregationPriceIncrement The price increment interval for aggregating order book data.
   *                                  Must be a positive BigDecimal. If null, the API's default aggregation is applied.
   * @return A {@link CoinbaseProductPriceBookResponse} containing:
   *         - The {@link CoinbasePriceBook} with bid and ask price levels, quantities, and timestamps,
   *         - The absolute spread between bid and ask,
   *         - The mid-market price (average of bid and ask),
   *         - The spread in basis points (relative to mid-market),
   *         - The last traded price for the product.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseProductPriceBookResponse getProductBook(String productId, Integer limit, Double aggregationPriceIncrement)
      throws IOException {
    Objects.requireNonNull(productId, "productId cannot be null");

    return coinbaseAdvancedTrade.getProductBook(authTokenCreator, productId, limit, aggregationPriceIncrement != null ? aggregationPriceIncrement.toString() : null);
  }

}
