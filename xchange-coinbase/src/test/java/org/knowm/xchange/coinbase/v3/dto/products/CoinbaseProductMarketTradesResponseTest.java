package org.knowm.xchange.coinbase.v3.dto.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

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

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getMarketTrades(), "Market trades list should not be null");
    assertTrue(response.getMarketTrades().isEmpty(), "Market trades list should be empty");
    assertEquals(new BigDecimal("50000"), response.getBestBid(), "Best bid should match");
    assertEquals(new BigDecimal("50001"), response.getBestAsk(), "Best ask should match");
  }

  @Test
  public void testWithEmptyTradesList() throws Exception {
    String json = "{\"trades\": [], \"best_bid\": \"50000\", \"best_ask\": \"50001\"}";
    CoinbaseProductMarketTradesResponse response = 
        mapper.readValue(json, CoinbaseProductMarketTradesResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getMarketTrades(), "Market trades list should not be null");
    assertTrue(response.getMarketTrades().isEmpty(), "Market trades list should be empty");
  }

  @Test
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"trades\": []}";
    CoinbaseProductMarketTradesResponse response = 
        mapper.readValue(json, CoinbaseProductMarketTradesResponse.class);

    assertThrows(UnsupportedOperationException.class, () -> response.getMarketTrades().add(null));
  }
}

