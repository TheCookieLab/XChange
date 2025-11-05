package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class CoinbaseOrderDetailJsonTest {

  @Test
  public void testDeserializeOrderDetail() throws Exception {
    String json = "{\n" +
        "  \"order\": {\n" +
        "    \"order_id\": \"abc123\",\n" +
        "    \"client_order_id\": \"c-1\",\n" +
        "    \"side\": \"BUY\",\n" +
        "    \"product_id\": \"BTC-USD\",\n" +
        "    \"status\": \"FILLED\",\n" +
        "    \"average_filled_price\": \"30000\",\n" +
        "    \"filled_size\": \"0.01\",\n" +
        "    \"total_fees\": \"0.3\",\n" +
        "    \"size\": \"0.01\",\n" +
        "    \"price\": \"30000\",\n" +
        "    \"created_time\": \"2024-01-01T00:00:00Z\"\n" +
        "  }\n" +
        "}";

    ObjectMapper mapper = new ObjectMapper();
    CoinbaseOrderDetailResponse response = mapper.readValue(json, CoinbaseOrderDetailResponse.class);
    assertNotNull(response);
    assertNotNull(response.getOrder());
    assertEquals("abc123", response.getOrder().getOrderId());
    assertEquals("BTC-USD", response.getOrder().getProductId());
  }
}


