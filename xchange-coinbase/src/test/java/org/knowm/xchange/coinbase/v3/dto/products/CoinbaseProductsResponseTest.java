package org.knowm.xchange.coinbase.v3.dto.products;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Unit tests for preserving the required Coinbase products member. */
public class CoinbaseProductsResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testPreservesMissingProductsList() throws Exception {
    String json = "{}";
    CoinbaseProductsResponse response = mapper.readValue(json, CoinbaseProductsResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNull(response.getProducts(), "Missing products must remain distinguishable from an empty catalog");
  }

  @Test
  public void testWithEmptyProductsList() throws Exception {
    String json = "{\"products\": []}";
    CoinbaseProductsResponse response = mapper.readValue(json, CoinbaseProductsResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getProducts(), "Products list should not be null");
    assertTrue(response.getProducts().isEmpty(), "Products list should be empty");
  }

  @Test
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"products\": []}";
    CoinbaseProductsResponse response = mapper.readValue(json, CoinbaseProductsResponse.class);

    assertThrows(UnsupportedOperationException.class, () -> response.getProducts().add(null));
  }
}

