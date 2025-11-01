package org.knowm.xchange.coinbase.v3.dto.paymentmethods;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

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

    assertNotNull("Response should not be null", response);
    assertNotNull("Payment methods list should not be null", response.getPaymentMethods());
    assertTrue("Payment methods list should be empty", response.getPaymentMethods().isEmpty());
  }

  @Test
  public void testWithEmptyMethods() throws Exception {
    String json = "{\"payment_methods\": []}";
    CoinbasePaymentMethodsResponse response = 
        mapper.readValue(json, CoinbasePaymentMethodsResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Payment methods list should not be null", response.getPaymentMethods());
    assertTrue("Payment methods list should be empty", response.getPaymentMethods().isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"payment_methods\": []}";
    CoinbasePaymentMethodsResponse response = 
        mapper.readValue(json, CoinbasePaymentMethodsResponse.class);

    // Should throw UnsupportedOperationException
    response.getPaymentMethods().add(null);
  }
}

