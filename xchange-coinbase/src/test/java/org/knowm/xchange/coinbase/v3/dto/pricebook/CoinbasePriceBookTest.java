package org.knowm.xchange.coinbase.v3.dto.pricebook;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/**
 * Unit tests for CoinbasePriceBook.
 * Tests null-safe list handling for both bids and asks lists to prevent NPEs when API returns null.
 */
public class CoinbasePriceBookTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testWithNullBidsAndAsks() throws Exception {
    String json = "{\"product_id\": \"BTC-USD\", \"time\": \"2024-01-01T00:00:00Z\"}";
    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    assertNotNull("PriceBook should not be null", priceBook);
    assertNotNull("Bids list should not be null", priceBook.getBids());
    assertNotNull("Asks list should not be null", priceBook.getAsks());
    assertTrue("Bids list should be empty", priceBook.getBids().isEmpty());
    assertTrue("Asks list should be empty", priceBook.getAsks().isEmpty());
  }

  @Test
  public void testWithEmptyBidsAndAsks() throws Exception {
    String json = "{\"product_id\": \"BTC-USD\", \"bids\": [], \"asks\": [], \"time\": \"2024-01-01T00:00:00Z\"}";
    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    assertNotNull("PriceBook should not be null", priceBook);
    assertNotNull("Bids list should not be null", priceBook.getBids());
    assertNotNull("Asks list should not be null", priceBook.getAsks());
    assertTrue("Bids list should be empty", priceBook.getBids().isEmpty());
    assertTrue("Asks list should be empty", priceBook.getAsks().isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testBidsReturnsImmutableList() throws Exception {
    String json = "{\"product_id\": \"BTC-USD\", \"bids\": [], \"asks\": [], \"time\": \"2024-01-01T00:00:00Z\"}";
    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    // Should throw UnsupportedOperationException
    priceBook.getBids().add(null);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testAsksReturnsImmutableList() throws Exception {
    String json = "{\"product_id\": \"BTC-USD\", \"bids\": [], \"asks\": [], \"time\": \"2024-01-01T00:00:00Z\"}";
    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    // Should throw UnsupportedOperationException
    priceBook.getAsks().add(null);
  }
}

