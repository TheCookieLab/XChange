package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;
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

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSpecificationWithOverride();
    coinbase = ExchangeRestProxyBuilder.forInterface(Coinbase.class, spec).build();
  }

  @Test
  public void testGetTime() throws IOException {
    CoinbaseTimeResponse response = coinbase.getTime();
    
    assertNotNull("Time response should not be null", response);
    assertNotNull("ISO timestamp should not be null", response.getIso());
    assertFalse("ISO timestamp should not be empty", response.getIso().isEmpty());
    // Verify ISO 8601 format (contains T and Z)
    assertTrue("ISO timestamp should be in ISO 8601 format", 
        response.getIso().contains("T") && response.getIso().contains("Z"));
  }

  @Test
  public void testGetPublicProductBook() throws IOException {
    CoinbaseProductPriceBookResponse response = coinbase.getPublicProductBook(
        TEST_PRODUCT_ID, 10, null);
    
    assertNotNull("Product book response should not be null", response);
    assertNotNull("Price book should not be null", response.getPriceBook());
    assertEquals("Product ID should match", TEST_PRODUCT_ID,
        response.getPriceBook().getProductId());
    assertNotNull("Bids should not be null", response.getPriceBook().getBids());
    assertNotNull("Asks should not be null", response.getPriceBook().getAsks());
    assertFalse("Bids should not be empty", response.getPriceBook().getBids().isEmpty());
    assertFalse("Asks should not be empty", response.getPriceBook().getAsks().isEmpty());
  }

  @Test
  public void testGetPublicProductBookWithLimit() throws IOException {
    int limit = 5;
    CoinbaseProductPriceBookResponse response = coinbase.getPublicProductBook(
        TEST_PRODUCT_ID, limit, null);
    
    assertNotNull("Product book response should not be null", response);
    assertNotNull("Price book should not be null", response.getPriceBook());
    // Verify limit is respected (should have at most 'limit' entries)
    assertTrue("Bids should respect limit", 
        response.getPriceBook().getBids().size() <= limit);
    assertTrue("Asks should respect limit", 
        response.getPriceBook().getAsks().size() <= limit);
  }

  @Test
  public void testListPublicProducts() throws IOException {
    CoinbaseProductsResponse response = coinbase.listPublicProducts(10, null, null, null, null, null, null, null);
    
    assertNotNull("Products response should not be null", response);
    assertNotNull("Products list should not be null", response.getProducts());
    assertFalse("Products should not be empty", response.getProducts().isEmpty());
    // Verify at least one product has required fields
    assertNotNull("First product should have product ID", 
        response.getProducts().get(0).getProductId());
  }

  @Test
  public void testListPublicProductsWithLimit() throws IOException {
    int limit = 5;
    CoinbaseProductsResponse response = coinbase.listPublicProducts(limit, null, null, null, null, null, null, null);
    
    assertNotNull("Products response should not be null", response);
    assertNotNull("Products list should not be null", response.getProducts());
    assertTrue("Should respect limit", response.getProducts().size() <= limit);
  }

  @Test
  public void testListPublicProductsWithProductType() throws IOException {
    CoinbaseProductsResponse response = coinbase.listPublicProducts(
        null, null, "SPOT", null, null, null, null, null);
    
    assertNotNull("Products response should not be null", response);
    assertNotNull("Products list should not be null", response.getProducts());
    // This test primarily verifies the endpoint accepts the product_type filter parameter.
    assertFalse("Products should not be empty", response.getProducts().isEmpty());
  }

  @Test
  public void testGetPublicProduct() throws IOException {
    CoinbaseProductResponse response = coinbase.getPublicProduct(TEST_PRODUCT_ID);
    
    assertNotNull("Product response should not be null", response);
    assertEquals("Product ID should match", TEST_PRODUCT_ID, response.getProductId());
    // This endpoint can return additional metadata (e.g. base/quote currency ids, product type/venue)
    // and may include futures details when the selected product_id is a futures contract.
  }

  @Test
  public void testGetPublicProductCandles() throws IOException {
    // Get candles for the last hour (3600 seconds granularity)
    CoinbaseProductCandlesResponse response = coinbase.getPublicProductCandles(
        TEST_PRODUCT_ID, null, null, "ONE_HOUR", 10);
    
    assertNotNull("Candles response should not be null", response);
    assertNotNull("Candles list should not be null", response.getCandles());
    assertFalse("Candles should not be empty", response.getCandles().isEmpty());
    // Verify limit is respected
    assertTrue("Should respect limit", response.getCandles().size() <= 10);
    
    // Verify first candle has required fields
    var firstCandle = response.getCandles().get(0);
    assertNotNull("Candle start time should not be null", firstCandle.getStart());
    assertNotNull("Candle low should not be null", firstCandle.getLow());
    assertNotNull("Candle high should not be null", firstCandle.getHigh());
    assertNotNull("Candle open should not be null", firstCandle.getOpen());
    assertNotNull("Candle close should not be null", firstCandle.getClose());
    assertNotNull("Candle volume should not be null", firstCandle.getVolume());
  }

  @Test
  public void testGetPublicProductCandlesWithTimeRange() throws IOException {
    // Use a time range for the last day
    String end = String.valueOf(System.currentTimeMillis() / 1000);
    String start = String.valueOf((System.currentTimeMillis() / 1000) - 86400); // 24 hours ago
    
    CoinbaseProductCandlesResponse response = coinbase.getPublicProductCandles(
        TEST_PRODUCT_ID, start, end, "ONE_HOUR", null);
    
    assertNotNull("Candles response should not be null", response);
    assertNotNull("Candles list should not be null", response.getCandles());
    // Should have approximately 24 candles (one per hour)
    assertTrue("Should have candles in the time range", 
        response.getCandles().size() > 0 && response.getCandles().size() <= 24);
  }

  @Test
  public void testGetPublicMarketTrades() throws IOException {
    CoinbaseProductMarketTradesResponse response = coinbase.getPublicMarketTrades(
        TEST_PRODUCT_ID, 10, null, null);
    
    assertNotNull("Market trades response should not be null", response);
    assertNotNull("Trades list should not be null", response.getMarketTrades());
    assertFalse("Trades should not be empty", response.getMarketTrades().isEmpty());
    // Verify limit is respected
    assertTrue("Should respect limit", response.getMarketTrades().size() <= 10);
    
    // Verify first trade has required fields
    var firstTrade = response.getMarketTrades().get(0);
    assertNotNull("Trade ID should not be null", firstTrade.getTradeId());
    assertEquals("Product ID should match", TEST_PRODUCT_ID, firstTrade.getProductId());
    assertNotNull("Price should not be null", firstTrade.getPrice());
    assertNotNull("Size should not be null", firstTrade.getSize());
    assertNotNull("Side should not be null", firstTrade.getSide());
  }

  @Test
  public void testGetPublicMarketTradesWithLimit() throws IOException {
    int limit = 5;
    CoinbaseProductMarketTradesResponse response = coinbase.getPublicMarketTrades(
        TEST_PRODUCT_ID, limit, null, null);
    
    assertNotNull("Market trades response should not be null", response);
    assertNotNull("Trades list should not be null", response.getMarketTrades());
    assertTrue("Should respect limit", response.getMarketTrades().size() <= limit);
  }

  @Test
  public void testGetPublicMarketTradesWithBestBidAsk() throws IOException {
    CoinbaseProductMarketTradesResponse response = coinbase.getPublicMarketTrades(
        TEST_PRODUCT_ID, 1, null, null);
    
    assertNotNull("Market trades response should not be null", response);
    // Best bid/ask are optional but should be present if available
    if (response.getBestBid() != null) {
      assertTrue("Best bid should be positive", 
          response.getBestBid().compareTo(java.math.BigDecimal.ZERO) > 0);
    }
    if (response.getBestAsk() != null) {
      assertTrue("Best ask should be positive", 
          response.getBestAsk().compareTo(java.math.BigDecimal.ZERO) > 0);
    }
  }
}
