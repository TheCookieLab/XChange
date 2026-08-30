package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.Test;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;

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
        "    \"order_type\": \"MARKET\",\n" +
        "    \"time_in_force\": \"IOC\",\n" +
        "    \"order_configuration\": {\"market_market_ioc\":{\"base_size\":\"0.01\"}},\n" +
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
    assertEquals("MARKET", response.getOrder().getExchangeOrderType());
    assertEquals(Order.OrderType.BID, response.getOrder().getOrderType());
    assertEquals(new java.math.BigDecimal("0.01"),
        response.getOrder().getOrderConfiguration().getMarketMarketIoc().getBaseSize());
  }

  @Test
  public void testDeserializeAndAdaptCdeLimitFokOrderDetail() throws Exception {
    String json =
        "{\"order\":{\"order_id\":\"fok-1\",\"client_order_id\":\"c-fok\","
            + "\"side\":\"BUY\",\"product_id\":\"ETP-20DEC30-CDE\",\"status\":\"FILLED\","
            + "\"order_type\":\"LIMIT\",\"time_in_force\":\"FOK\","
            + "\"order_configuration\":{\"limit_limit_fok\":{"
            + "\"base_size\":\"2.5\",\"limit_price\":\"2505.25\"}},"
            + "\"created_time\":\"2026-02-08T00:00:00Z\"}}";

    CoinbaseOrderDetailResponse response =
        new ObjectMapper().readValue(json, CoinbaseOrderDetailResponse.class);
    assertEquals(new BigDecimal("2.5"),
        response.getOrder().getOrderConfiguration().getLimitLimitFok().getBaseSize());
    assertEquals(new BigDecimal("2505.25"),
        response.getOrder().getOrderConfiguration().getLimitLimitFok().getLimitPrice());

    Order adapted = CoinbaseAdapters.adaptOrder(response.getOrder());
    assertTrue(adapted instanceof LimitOrder);
    assertEquals(new BigDecimal("2.5"), adapted.getOriginalAmount());
    assertEquals(new BigDecimal("2505.25"), ((LimitOrder) adapted).getLimitPrice());
  }
}


