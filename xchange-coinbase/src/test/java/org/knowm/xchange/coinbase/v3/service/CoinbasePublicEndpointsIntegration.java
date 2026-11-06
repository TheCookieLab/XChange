package org.knowm.xchange.coinbase.v3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.CoinbaseTestUtils;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseTimeResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseProductPriceBookResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductsResponse;

/**
 * Integration tests for Coinbase Advanced Trade API public endpoints.
 * 
 * <p>These tests exercise all 6 public endpoints that do not require authentication:
 * <ul>
 *   <li>GET /api/v3/brokerage/time - Get Server Time</li>
 *   <li>GET /api/v3/brokerage/market/product_book - Get Public Product Book</li>
 *   <li>GET /api/v3/brokerage/market/products - List Public Products</li>
 *   <li>GET /api/v3/brokerage/market/products/{product_id} - Get Public Product</li>
 *   <li>GET /api/v3/brokerage/market/products/{product_id}/candles - Get Public Product Candles</li>
 *   <li>GET /api/v3/brokerage/market/products/{product_id}/ticker - Get Public Market Trades</li>
 * </ul>
 * 
 * <p><b>Note:</b> These endpoints work in both production and sandbox environments.
 * Public endpoints have 1s cache enabled by default.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * mvn test -Dtest=CoinbasePublicEndpointsIntegration
 * </pre>
 * 
 * @see <a href="https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/rest-api">Coinbase Advanced Trade API Docs</a>
 */
public class CoinbasePublicEndpointsIntegration {

  static Coinbase coinbase;
  private static final String TEST_PRODUCT_ID = "BTC-USD";

  @BeforeAll
  public static void beforeClass() {
    // Applying the specification binds the Coinbase rate-limit context the proxy admits through.
    ExchangeSpecification spec =
        ExchangeFactory.INSTANCE
            .createExchange(CoinbaseTestUtils.createSpecificationWithOverride())
            .getExchangeSpecification();
    coinbase = ExchangeRestProxyBuilder.forInterface(Coinbase.class, spec).build();
  }

  @Test
  public void testGetTime() throws IOException {
    CoinbaseTimeResponse response = coinbase.getTime();
    
    assertNotNull(response, "Time response should not be null");
    assertNotNull(response.getIso(), "ISO timestamp should not be null");
    assertFalse(response.getIso().isEmpty(), "ISO timestamp should not be empty");
    // Verify ISO 8601 format (contains T and Z)
    assertTrue(response.getIso().contains("T") && response.getIso().contains("Z"), "ISO timestamp should be in ISO 8601 format");
  }

  @Test
  public void testGetPublicProductBook() throws IOException {
    CoinbaseProductPriceBookResponse response = coinbase.getPublicProductBook(
        TEST_PRODUCT_ID, 10, null);
    
    assertNotNull(response, "Product book response should not be null");
    assertNotNull(response.getPriceBook(), "Price book should not be null");
    assertEquals(TEST_PRODUCT_ID, response.getPriceBook().getProductId(), "Product ID should match");
    assertNotNull(response.getPriceBook().getBids(), "Bids should not be null");
    assertNotNull(response.getPriceBook().getAsks(), "Asks should not be null");
    assertFalse(response.getPriceBook().getBids().isEmpty(), "Bids should not be empty");
    assertFalse(response.getPriceBook().getAsks().isEmpty(), "Asks should not be empty");
  }

  @Test
  public void testGetPublicProductBookWithLimit() throws IOException {
    int limit = 5;
    CoinbaseProductPriceBookResponse response = coinbase.getPublicProductBook(
        TEST_PRODUCT_ID, limit, null);
    
    assertNotNull(response, "Product book response should not be null");
    assertNotNull(response.getPriceBook(), "Price book should not be null");
    // Verify limit is respected (should have at most 'limit' entries)
    assertTrue(response.getPriceBook().getBids().size() <= limit, "Bids should respect limit");
    assertTrue(response.getPriceBook().getAsks().size() <= limit, "Asks should respect limit");
  }

  @Test
  public void testListPublicProducts() throws IOException {
    CoinbaseProductsResponse response = coinbase.listPublicProducts(10, null, null, null, null, null, null, null);
    
    assertNotNull(response, "Products response should not be null");
    assertNotNull(response.getProducts(), "Products list should not be null");
    assertFalse(response.getProducts().isEmpty(), "Products should not be empty");
    // Verify at least one product has required fields
    assertNotNull(response.getProducts().get(0).getProductId(), "First product should have product ID");
  }

  @Test
  public void testListPublicProductsWithLimit() throws IOException {
    int limit = 5;
    CoinbaseProductsResponse response = coinbase.listPublicProducts(limit, null, null, null, null, null, null, null);
    
    assertNotNull(response, "Products response should not be null");
    assertNotNull(response.getProducts(), "Products list should not be null");
    assertTrue(response.getProducts().size() <= limit, "Should respect limit");
  }

  @Test
  public void testListPublicProductsWithProductType() throws IOException {
    CoinbaseProductsResponse response = coinbase.listPublicProducts(
        null, null, "SPOT", null, null, null, null, null);
    
    assertNotNull(response, "Products response should not be null");
    assertNotNull(response.getProducts(), "Products list should not be null");
    // This test primarily verifies the endpoint accepts the product_type filter parameter.
    assertFalse(response.getProducts().isEmpty(), "Products should not be empty");
  }

  @Test
  public void testGetPublicProduct() throws IOException {
    CoinbaseProductResponse response = coinbase.getPublicProduct(TEST_PRODUCT_ID);
    
    assertNotNull(response, "Product response should not be null");
    assertEquals(TEST_PRODUCT_ID, response.getProductId(), "Product ID should match");
    // This endpoint can return additional metadata (e.g. base/quote currency ids, product type/venue)
    // and may include futures details when the selected product_id is a futures contract.
  }

  @Test
  public void testGetPublicProductCandles() throws IOException {
    // Get candles for the last hour (3600 seconds granularity)
    CoinbaseProductCandlesResponse response = coinbase.getPublicProductCandles(
        TEST_PRODUCT_ID, null, null, "ONE_HOUR", 10);
    
    assertNotNull(response, "Candles response should not be null");
    assertNotNull(response.getCandles(), "Candles list should not be null");
    assertFalse(response.getCandles().isEmpty(), "Candles should not be empty");
    // Verify limit is respected
    assertTrue(response.getCandles().size() <= 10, "Should respect limit");
    
    // Verify first candle has required fields
    var firstCandle = response.getCandles().get(0);
    assertNotNull(firstCandle.getStart(), "Candle start time should not be null");
    assertNotNull(firstCandle.getLow(), "Candle low should not be null");
    assertNotNull(firstCandle.getHigh(), "Candle high should not be null");
    assertNotNull(firstCandle.getOpen(), "Candle open should not be null");
    assertNotNull(firstCandle.getClose(), "Candle close should not be null");
    assertNotNull(firstCandle.getVolume(), "Candle volume should not be null");
  }

  @Test
  public void testGetPublicProductCandlesWithTimeRange() throws IOException {
    // Use a time range for the last day
    String end = String.valueOf(System.currentTimeMillis() / 1000);
    String start = String.valueOf((System.currentTimeMillis() / 1000) - 86400); // 24 hours ago
    
    CoinbaseProductCandlesResponse response = coinbase.getPublicProductCandles(
        TEST_PRODUCT_ID, start, end, "ONE_HOUR", null);
    
    assertNotNull(response, "Candles response should not be null");
    assertNotNull(response.getCandles(), "Candles list should not be null");
    // Should have approximately 24 candles (one per hour)
    assertTrue(response.getCandles().size() > 0 && response.getCandles().size() <= 24, "Should have candles in the time range");
  }

  @Test
  public void testGetPublicMarketTrades() throws IOException {
    CoinbaseProductMarketTradesResponse response = coinbase.getPublicMarketTrades(
        TEST_PRODUCT_ID, 10, null, null);
    
    assertNotNull(response, "Market trades response should not be null");
    assertNotNull(response.getMarketTrades(), "Trades list should not be null");
    assertFalse(response.getMarketTrades().isEmpty(), "Trades should not be empty");
    // Verify limit is respected
    assertTrue(response.getMarketTrades().size() <= 10, "Should respect limit");
    
    // Verify first trade has required fields
    var firstTrade = response.getMarketTrades().get(0);
    assertNotNull(firstTrade.getTradeId(), "Trade ID should not be null");
    assertEquals(TEST_PRODUCT_ID, firstTrade.getProductId(), "Product ID should match");
    assertNotNull(firstTrade.getPrice(), "Price should not be null");
    assertNotNull(firstTrade.getSize(), "Size should not be null");
    assertNotNull(firstTrade.getSide(), "Side should not be null");
  }

  @Test
  public void testGetPublicMarketTradesWithLimit() throws IOException {
    int limit = 5;
    CoinbaseProductMarketTradesResponse response = coinbase.getPublicMarketTrades(
        TEST_PRODUCT_ID, limit, null, null);
    
    assertNotNull(response, "Market trades response should not be null");
    assertNotNull(response.getMarketTrades(), "Trades list should not be null");
    assertTrue(response.getMarketTrades().size() <= limit, "Should respect limit");
  }

  @Test
  public void testGetPublicMarketTradesWithBestBidAsk() throws IOException {
    CoinbaseProductMarketTradesResponse response = coinbase.getPublicMarketTrades(
        TEST_PRODUCT_ID, 1, null, null);
    
    assertNotNull(response, "Market trades response should not be null");
    // Best bid/ask are optional but should be present if available
    if (response.getBestBid() != null) {
      assertTrue(response.getBestBid().compareTo(java.math.BigDecimal.ZERO) > 0, "Best bid should be positive");
    }
    if (response.getBestAsk() != null) {
      assertTrue(response.getBestAsk().compareTo(java.math.BigDecimal.ZERO) > 0, "Best ask should be positive");
    }
  }
}
