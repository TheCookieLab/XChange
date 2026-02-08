package org.knowm.xchange.coinbase.v3.dto.products;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.Test;

/**
 * Unit tests for CoinbaseProductResponse JSON parsing.
 * Verifies correct deserialization of product/market data from Coinbase API responses.
 */
public class CoinbaseProductJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testDeserializeProductResponse() throws IOException {
    InputStream is = CoinbaseProductJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/v3/products/example-product-response.json");
    
    CoinbaseProductResponse product = mapper.readValue(is, CoinbaseProductResponse.class);

    assertNotNull("Product should not be null", product);
    assertEquals("Product ID should match", "BTC-USD", product.getProductId());
    assertEquals("Product type should match", "SPOT", product.getProductType());
    assertEquals("Product venue should match", "CBE", product.getProductVenue());
    assertEquals("Base currency id should match", "BTC", product.getBaseCurrencyId());
    assertEquals("Quote currency id should match", "USD", product.getQuoteCurrencyId());
    assertTrue("Spot products should not include futures details", product.getFutureProductDetails() == null);
  }

  @Test
  public void testDeserializeProductWithMinimalFields() throws Exception {
    String json = "{\n" +
        "  \"product_id\": \"ETH-USD\"\n" +
        "}";

    CoinbaseProductResponse product = mapper.readValue(json, CoinbaseProductResponse.class);

    assertNotNull("Product should not be null", product);
    assertEquals("Product ID should match", "ETH-USD", product.getProductId());
    assertTrue("Minimal payload should not have product type", product.getProductType() == null);
    assertTrue("Minimal payload should not have product venue", product.getProductVenue() == null);
  }

  @Test
  public void testDeserializeFutureProductDetails() throws Exception {
    String json = "{\n"
        + "  \"product_id\": \"BIP-20DEC30-CDE\",\n"
        + "  \"product_type\": \"FUTURE\",\n"
        + "  \"quote_currency_id\": \"USD\",\n"
        + "  \"future_product_details\": {\n"
        + "    \"contract_root_unit\": \"BTC\",\n"
        + "    \"funding_rate\": \"0.000024\",\n"
        + "    \"funding_time\": \"2026-02-08T14:00:00Z\",\n"
        + "    \"intraday_margin_rate\": {\n"
        + "      \"long_margin_rate\": \"0.1000185\",\n"
        + "      \"short_margin_rate\": \"0.1000008\"\n"
        + "    },\n"
        + "    \"overnight_margin_rate\": {\n"
        + "      \"long_margin_rate\": \"0.245625\",\n"
        + "      \"short_margin_rate\": \"0.306375\"\n"
        + "    }\n"
        + "  }\n"
        + "}";

    CoinbaseProductResponse product = mapper.readValue(json, CoinbaseProductResponse.class);

    assertNotNull("Product should not be null", product);
    assertEquals("Product ID should match", "BIP-20DEC30-CDE", product.getProductId());
    assertEquals("Product type should match", "FUTURE", product.getProductType());
    assertEquals("Quote currency id should match", "USD", product.getQuoteCurrencyId());

    CoinbaseFutureProductDetails details = product.getFutureProductDetails();
    assertNotNull("Future product details should not be null", details);
    assertEquals("Contract root unit should match", "BTC", details.getContractRootUnit());
    assertEquals(new BigDecimal("0.000024"), details.getFundingRate());
    assertEquals(Instant.parse("2026-02-08T14:00:00Z"), details.getFundingTime());

    assertNotNull(details.getIntradayMarginRate());
    assertEquals(new BigDecimal("0.1000185"), details.getIntradayMarginRate().getLongMarginRate());
    assertEquals(new BigDecimal("0.1000008"), details.getIntradayMarginRate().getShortMarginRate());

    assertNotNull(details.getOvernightMarginRate());
    assertEquals(new BigDecimal("0.245625"), details.getOvernightMarginRate().getLongMarginRate());
    assertEquals(new BigDecimal("0.306375"), details.getOvernightMarginRate().getShortMarginRate());
  }

  @Test
  public void testDeserializeCandlesResponse() throws IOException {
    InputStream is = CoinbaseProductJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/v3/products/example-candles-response.json");
    
    CoinbaseProductCandlesResponse response = mapper.readValue(is, 
        CoinbaseProductCandlesResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Candles should not be null", response.getCandles());
    assertEquals("Should have 2 candles", 2, response.getCandles().size());
    
    // Verify first candle
    CoinbaseProductCandle candle1 = response.getCandles().get(0);
    assertEquals("Start time should match", "1609459200", candle1.getStart());
    assertEquals("Low should match", new BigDecimal("49000.00"), candle1.getLow());
    assertEquals("High should match", new BigDecimal("51000.00"), candle1.getHigh());
    assertEquals("Open should match", new BigDecimal("50000.00"), candle1.getOpen());
    assertEquals("Close should match", new BigDecimal("50500.00"), candle1.getClose());
    assertEquals("Volume should match", new BigDecimal("100.5"), candle1.getVolume());
    
    // Verify second candle
    CoinbaseProductCandle candle2 = response.getCandles().get(1);
    assertEquals("Start time should match", "1609462800", candle2.getStart());
    assertEquals("Close should match", new BigDecimal("51500.00"), candle2.getClose());
    assertEquals("Volume should match", new BigDecimal("150.75"), candle2.getVolume());
  }

  @Test
  public void testDeserializeMarketTradesList() throws Exception {
    String json = "{\n" +
        "  \"trades\": [\n" +
        "    {\n" +
        "      \"trade_id\": \"t1\",\n" +
        "      \"product_id\": \"BTC-USD\",\n" +
        "      \"price\": \"50000\",\n" +
        "      \"size\": \"0.1\",\n" +
        "      \"side\": \"BUY\",\n" +
        "      \"time\": \"2024-01-01T00:00:00Z\",\n" +
        "      \"trade_time\": \"12345\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"best_bid\": \"49999\",\n" +
        "  \"best_ask\": \"50001\"\n" +
        "}";

    CoinbaseProductMarketTradesResponse response = mapper.readValue(json, 
        CoinbaseProductMarketTradesResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Trades should not be null", response.getMarketTrades());
    assertEquals("Should have 1 trade", 1, response.getMarketTrades().size());
    assertEquals("Best bid should match", new BigDecimal("49999"), response.getBestBid());
    assertEquals("Best ask should match", new BigDecimal("50001"), response.getBestAsk());
    
    CoinbaseMarketTrade trade = response.getMarketTrades().get(0);
    assertEquals("Trade ID should match", "t1", trade.getTradeId());
    assertEquals("Product ID should match", "BTC-USD", trade.getProductId());
    assertEquals("Price should match", new BigDecimal("50000"), trade.getPrice());
    assertEquals("Size should match", new BigDecimal("0.1"), trade.getSize());
    assertEquals("Side should match", "BUY", trade.getSide());
  }
}
