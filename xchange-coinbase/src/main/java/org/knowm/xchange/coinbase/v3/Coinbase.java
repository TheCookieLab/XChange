package org.knowm.xchange.coinbase.v3;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseTimeResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseProductPriceBookResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductsResponse;

/**
 * Coinbase Advanced Trade API public endpoints.
 *
 * <p>These endpoints do not require authentication. All public endpoints have 1s cache enabled. To
 * bypass caching, set the cache-control: no-cache header on API requests.
 */
@Path("/api/v3/brokerage")
@Produces(MediaType.APPLICATION_JSON)
public interface Coinbase {

  /**
   * Get the current server time.
   *
   * <p>Endpoint: GET /time
   *
   * @return Server time response with ISO 8601 timestamp
   * @throws IOException if the request fails
   * @throws CoinbaseException if the API returns an error
   */
  @GET
  @Path("time")
  CoinbaseTimeResponse getTime() throws IOException, CoinbaseException;

  /**
   * Get a list of bids/asks for a single product.
   *
   * <p>Endpoint: GET /market/product_book
   *
   * @param productId The trading pair (e.g. 'BTC-USD')
   * @param limit The number of bid/asks to be returned
   * @param aggregationPriceIncrement The minimum price intervals at which buy and sell orders are
   *     grouped or combined in the order book
   * @return Product price book response
   * @throws IOException if the request fails
   * @throws CoinbaseException if the API returns an error
   */
  @GET
  @Path("market/product_book")
  CoinbaseProductPriceBookResponse getPublicProductBook(
      @QueryParam("product_id") String productId,
      @QueryParam("limit") Integer limit,
      @QueryParam("aggregation_price_increment") String aggregationPriceIncrement)
      throws IOException, CoinbaseException;

  /**
   * Get a list of the available currency pairs for trading.
   *
   * <p>Endpoint: GET /market/products
   *
   * @param limit The number of products to be returned
   * @param offset The number of products to skip before returning
   * @param productType Only returns products matching this product type. By default, returns all
   *     product types
   * @param productIds The list of trading pairs (e.g. 'BTC-USD')
   * @param contractExpiryType Only returns products matching the contract expiry type. Only
   *     applicable if product_type is set to FUTURE
   * @param expiringContractStatus Only returns contracts with this status (default is UNEXPIRED)
   * @param getAllProducts If true, return all products of all product types (including expired
   *     futures contracts)
   * @param productsSortOrder The order in which products are returned. By default, products are
   *     returned in 24 hour volume descending (in quote)
   * @return Products response
   * @throws IOException if the request fails
   * @throws CoinbaseException if the API returns an error
   */
  @GET
  @Path("market/products")
  CoinbaseProductsResponse listPublicProducts(
      @QueryParam("limit") Integer limit,
      @QueryParam("offset") Integer offset,
      @QueryParam("product_type") String productType,
      @QueryParam("product_ids") List<String> productIds,
      @QueryParam("contract_expiry_type") String contractExpiryType,
      @QueryParam("expiring_contract_status") String expiringContractStatus,
      @QueryParam("get_all_products") Boolean getAllProducts,
      @QueryParam("products_sort_order") String productsSortOrder)
      throws IOException, CoinbaseException;

  /**
   * Get information on a single product by product ID.
   *
   * <p>Endpoint: GET /market/products/{product_id}
   *
   * @param productId The trading pair (e.g. 'BTC-USD')
   * @return Product response
   * @throws IOException if the request fails
   * @throws CoinbaseException if the API returns an error
   */
  @GET
  @Path("market/products/{product_id}")
  CoinbaseProductResponse getPublicProduct(@PathParam("product_id") String productId)
      throws IOException, CoinbaseException;

  /**
   * Get rates for a single product by product ID, grouped in buckets.
   *
   * <p>Endpoint: GET /market/products/{product_id}/candles
   *
   * @param productId The trading pair (e.g. 'BTC-USD')
   * @param start Timestamp for starting range of aggregations
   * @param end Timestamp for ending range of aggregations
   * @param granularity The time slice value for each candle
   * @param limit The number of candles to be returned
   * @return Product candles response
   * @throws IOException if the request fails
   * @throws CoinbaseException if the API returns an error
   */
  @GET
  @Path("market/products/{product_id}/candles")
  CoinbaseProductCandlesResponse getPublicProductCandles(
      @PathParam("product_id") String productId,
      @QueryParam("start") String start,
      @QueryParam("end") String end,
      @QueryParam("granularity") String granularity,
      @QueryParam("limit") Integer limit)
      throws IOException, CoinbaseException;

  /**
   * Get snapshot information by product ID about the last trades (ticks) and best bid/ask.
   *
   * <p>Endpoint: GET /market/products/{product_id}/ticker
   *
   * @param productId The trading pair (e.g. 'BTC-USD')
   * @param limit The number of trades to be returned
   * @param start The UNIX timestamp indicating the start of the time interval
   * @param end The UNIX timestamp indicating the end of the time interval
   * @return Product market trades response
   * @throws IOException if the request fails
   * @throws CoinbaseException if the API returns an error
   */
  @GET
  @Path("market/products/{product_id}/ticker")
  CoinbaseProductMarketTradesResponse getPublicMarketTrades(
      @PathParam("product_id") String productId,
      @QueryParam("limit") Integer limit,
      @QueryParam("start") String start,
      @QueryParam("end") String end)
      throws IOException, CoinbaseException;
}
