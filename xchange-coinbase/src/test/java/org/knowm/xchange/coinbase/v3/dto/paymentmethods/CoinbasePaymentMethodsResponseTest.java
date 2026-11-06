package org.knowm.xchange.coinbase.v3.dto.paymentmethods;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CoinbasePaymentMethodsResponse.
 * Tests null-safe list handling to prevent NPEs when API returns null for payment methods list.
 */
public class CoinbasePaymentMethodsResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testWithNullMethods() throws Exception {
    String json = "{}";
    CoinbasePaymentMethodsResponse response = 
        mapper.readValue(json, CoinbasePaymentMethodsResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getPaymentMethods(), "Payment methods list should not be null");
    assertTrue(response.getPaymentMethods().isEmpty(), "Payment methods list should be empty");
  }

  @Test
  public void testWithEmptyMethods() throws Exception {
    String json = "{\"payment_methods\": []}";
    CoinbasePaymentMethodsResponse response = 
        mapper.readValue(json, CoinbasePaymentMethodsResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getPaymentMethods(), "Payment methods list should not be null");
    assertTrue(response.getPaymentMethods().isEmpty(), "Payment methods list should be empty");
  }

  @Test
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"payment_methods\": []}";
    CoinbasePaymentMethodsResponse response = 
        mapper.readValue(json, CoinbasePaymentMethodsResponse.class);

    assertThrows(UnsupportedOperationException.class, () -> response.getPaymentMethods().add(null));
  }
}

