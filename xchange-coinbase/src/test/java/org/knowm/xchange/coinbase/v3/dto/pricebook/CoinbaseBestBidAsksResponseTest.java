package org.knowm.xchange.coinbase.v3.dto.pricebook;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CoinbaseBestBidAsksResponse.
 * Tests null-safe list handling to prevent NPEs when API returns null for price books list.
 */
public class CoinbaseBestBidAsksResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testWithNullPriceBooks() throws Exception {
    String json = "{}";
    CoinbaseBestBidAsksResponse response = 
        mapper.readValue(json, CoinbaseBestBidAsksResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getPriceBooks(), "Price books list should not be null");
    assertTrue(response.getPriceBooks().isEmpty(), "Price books list should be empty");
  }

  @Test
  public void testWithEmptyPriceBooks() throws Exception {
    String json = "{\"pricebooks\": []}";
    CoinbaseBestBidAsksResponse response = 
        mapper.readValue(json, CoinbaseBestBidAsksResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getPriceBooks(), "Price books list should not be null");
    assertTrue(response.getPriceBooks().isEmpty(), "Price books list should be empty");
  }

  @Test
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"pricebooks\": []}";
    CoinbaseBestBidAsksResponse response = 
        mapper.readValue(json, CoinbaseBestBidAsksResponse.class);

    assertThrows(UnsupportedOperationException.class, () -> response.getPriceBooks().add(null));
  }
}

