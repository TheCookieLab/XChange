package org.knowm.xchange.coinbase.v3.dto.products;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/** Unit tests for preserving the required Coinbase products member. */
public class CoinbaseProductsResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testPreservesMissingProductsList() throws Exception {
    String json = "{}";
    CoinbaseProductsResponse response = mapper.readValue(json, CoinbaseProductsResponse.class);

    assertNotNull("Response should not be null", response);
    assertNull("Missing products must remain distinguishable from an empty catalog", response.getProducts());
  }

  @Test
  public void testWithEmptyProductsList() throws Exception {
    String json = "{\"products\": []}";
    CoinbaseProductsResponse response = mapper.readValue(json, CoinbaseProductsResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Products list should not be null", response.getProducts());
    assertTrue("Products list should be empty", response.getProducts().isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"products\": []}";
    CoinbaseProductsResponse response = mapper.readValue(json, CoinbaseProductsResponse.class);

    // Should throw UnsupportedOperationException
    response.getProducts().add(null);
  }
}

