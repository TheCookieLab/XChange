package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Test;

public class CoinbaseListOrdersJsonTest {

  @Test
  public void testDeserializeListOrders() throws Exception {
    String json = "{\n" +
        "  \"orders\": [\n" +
        "    {\n" +
        "      \"order_id\": \"abc\",\n" +
        "      \"side\": \"BUY\",\n" +
        "      \"product_id\": \"BTC-USD\",\n" +
        "      \"status\": \"OPEN\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"cursor\": \"c1\"\n" +
        "}";

    ObjectMapper mapper = new ObjectMapper();
    CoinbaseListOrdersResponse response = mapper.readValue(json, CoinbaseListOrdersResponse.class);
    assertNotNull(response);
    List<CoinbaseOrderDetail> orders = response.getOrders();
    assertEquals(1, orders.size());
    assertEquals("abc", orders.get(0).getOrderId());
  }
}


