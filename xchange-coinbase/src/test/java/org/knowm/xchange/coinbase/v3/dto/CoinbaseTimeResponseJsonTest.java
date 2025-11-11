package org.knowm.xchange.coinbase.v3.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

/**
 * Unit tests for CoinbaseTimeResponse JSON parsing.
 * Verifies correct deserialization of time data from Coinbase API responses.
 */
public class CoinbaseTimeResponseJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testDeserializeTimeResponse() throws IOException {
    InputStream is = CoinbaseTimeResponseJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/v3/example-time-response.json");
    
    CoinbaseTimeResponse response = mapper.readValue(is, CoinbaseTimeResponse.class);

    assertNotNull("Response should not be null", response);
    assertEquals("ISO timestamp should match", "2024-01-01T12:00:00.000Z", response.getIso());
  }

  @Test
  public void testDeserializeTimeResponseWithMinimalFields() throws Exception {
    String json = "{\n" +
        "  \"iso\": \"2024-12-31T23:59:59.999Z\"\n" +
        "}";

    CoinbaseTimeResponse response = mapper.readValue(json, CoinbaseTimeResponse.class);

    assertNotNull("Response should not be null", response);
    assertEquals("ISO timestamp should match", "2024-12-31T23:59:59.999Z", response.getIso());
  }
}

