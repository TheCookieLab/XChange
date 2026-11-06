package org.knowm.xchange.coinbase.v3.dto.pricebook;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

    assertNotNull(priceBook, "PriceBook should not be null");
    assertNotNull(priceBook.getBids(), "Bids list should not be null");
    assertNotNull(priceBook.getAsks(), "Asks list should not be null");
    assertTrue(priceBook.getBids().isEmpty(), "Bids list should be empty");
    assertTrue(priceBook.getAsks().isEmpty(), "Asks list should be empty");
  }

  @Test
  public void testWithEmptyBidsAndAsks() throws Exception {
    String json = "{\"product_id\": \"BTC-USD\", \"bids\": [], \"asks\": [], \"time\": \"2024-01-01T00:00:00Z\"}";
    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    assertNotNull(priceBook, "PriceBook should not be null");
    assertNotNull(priceBook.getBids(), "Bids list should not be null");
    assertNotNull(priceBook.getAsks(), "Asks list should not be null");
    assertTrue(priceBook.getBids().isEmpty(), "Bids list should be empty");
    assertTrue(priceBook.getAsks().isEmpty(), "Asks list should be empty");
  }

  @Test
  public void testBidsReturnsImmutableList() throws Exception {
    String json = "{\"product_id\": \"BTC-USD\", \"bids\": [], \"asks\": [], \"time\": \"2024-01-01T00:00:00Z\"}";
    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    assertThrows(UnsupportedOperationException.class, () -> priceBook.getBids().add(null));
  }

  @Test
  public void testAsksReturnsImmutableList() throws Exception {
    String json = "{\"product_id\": \"BTC-USD\", \"bids\": [], \"asks\": [], \"time\": \"2024-01-01T00:00:00Z\"}";
    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    assertThrows(UnsupportedOperationException.class, () -> priceBook.getAsks().add(null));
  }
}

