package org.knowm.xchange.coinbase.v3.dto.products;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.Test;

/**
 * Unit tests for CoinbaseProductMarketTradesResponse.
 * Tests null-safe list handling to prevent NPEs when API returns null for market trades list.
 */
public class CoinbaseProductMarketTradesResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testWithNullTradesList() throws Exception {
    String json = "{\"best_bid\": \"50000\", \"best_ask\": \"50001\"}";
    CoinbaseProductMarketTradesResponse response = 
        mapper.readValue(json, CoinbaseProductMarketTradesResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Market trades list should not be null", response.getMarketTrades());
    assertTrue("Market trades list should be empty", response.getMarketTrades().isEmpty());
    assertEquals("Best bid should match", new BigDecimal("50000"), response.getBestBid());
    assertEquals("Best ask should match", new BigDecimal("50001"), response.getBestAsk());
  }

  @Test
  public void testWithEmptyTradesList() throws Exception {
    String json = "{\"trades\": [], \"best_bid\": \"50000\", \"best_ask\": \"50001\"}";
    CoinbaseProductMarketTradesResponse response = 
        mapper.readValue(json, CoinbaseProductMarketTradesResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Market trades list should not be null", response.getMarketTrades());
    assertTrue("Market trades list should be empty", response.getMarketTrades().isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"trades\": []}";
    CoinbaseProductMarketTradesResponse response = 
        mapper.readValue(json, CoinbaseProductMarketTradesResponse.class);

    // Should throw UnsupportedOperationException
    response.getMarketTrades().add(null);
  }
}

