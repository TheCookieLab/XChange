package org.knowm.xchange.coinbase.v3.dto.pricebook;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

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

    assertNotNull("Response should not be null", response);
    assertNotNull("Price books list should not be null", response.getPriceBooks());
    assertTrue("Price books list should be empty", response.getPriceBooks().isEmpty());
  }

  @Test
  public void testWithEmptyPriceBooks() throws Exception {
    String json = "{\"pricebooks\": []}";
    CoinbaseBestBidAsksResponse response = 
        mapper.readValue(json, CoinbaseBestBidAsksResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Price books list should not be null", response.getPriceBooks());
    assertTrue("Price books list should be empty", response.getPriceBooks().isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"pricebooks\": []}";
    CoinbaseBestBidAsksResponse response = 
        mapper.readValue(json, CoinbaseBestBidAsksResponse.class);

    // Should throw UnsupportedOperationException
    response.getPriceBooks().add(null);
  }
}

