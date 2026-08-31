package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
        "    \"leverage\": \"2\",\n" +
        "    \"margin_type\": \"CROSS\",\n" +
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
    assertEquals("2", response.getOrder().getLeverage());
    assertEquals(CoinbaseMarginType.CROSS, response.getOrder().getMarginType());
    Order adapted = CoinbaseAdapters.adaptOrder(response.getOrder());
    assertNotNull(adapted);
    assertEquals("2", adapted.getLeverage());
    assertEquals(new java.math.BigDecimal("0.01"),
        response.getOrder().getOrderConfiguration().getMarketMarketIoc().getBaseSize());
  }

  @Test
  public void testDeserializeUnknownMarginType() throws Exception {
    String json =
        "{\"order\":{\"order_id\":\"spot-1\",\"side\":\"BUY\",\"product_id\":\"BTC-USD\","
            + "\"status\":\"OPEN\",\"margin_type\":\"PORTFOLIO_MARGIN\","
            + "\"order_configuration\":{\"market_market_ioc\":{\"base_size\":\"0.01\"}},"
            + "\"created_time\":\"2026-02-08T00:00:00Z\"}}";

    CoinbaseOrderDetailResponse response =
        new ObjectMapper().readValue(json, CoinbaseOrderDetailResponse.class);
    assertEquals(CoinbaseMarginType.UNKNOWN_MARGIN_TYPE, response.getOrder().getMarginType());
  }

  @Test
  public void testAbsentAndBlankMarginTypeRemainNull() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    CoinbaseOrderDetail omitted =
        mapper.readValue("{\"order\":{\"order_id\":\"omitted\",\"side\":\"BUY\","
            + "\"product_id\":\"BTC-USD\",\"status\":\"OPEN\"}}",
            CoinbaseOrderDetailResponse.class).getOrder();
    CoinbaseOrderDetail explicitNull =
        mapper.readValue("{\"order\":{\"order_id\":\"null\",\"side\":\"BUY\","
            + "\"product_id\":\"BTC-USD\",\"status\":\"OPEN\",\"margin_type\":null}}",
            CoinbaseOrderDetailResponse.class).getOrder();
    CoinbaseOrderDetail blank =
        mapper.readValue("{\"order\":{\"order_id\":\"blank\",\"side\":\"BUY\","
            + "\"product_id\":\"BTC-USD\",\"status\":\"OPEN\",\"margin_type\":\"  \"}}",
            CoinbaseOrderDetailResponse.class).getOrder();

    assertNull(omitted.getMarginType());
    assertNull(explicitNull.getMarginType());
    assertNull(blank.getMarginType());
  }

  @Test
  public void testAdaptSorLimitIocOrderPreservesLimitPrice() throws Exception {
    String json =
        "{\"order\":{\"order_id\":\"sor-1\",\"side\":\"BUY\",\"product_id\":\"BTC-USD\","
            + "\"status\":\"OPEN\",\"order_configuration\":{\"sor_limit_ioc\":{"
            + "\"base_size\":\"0.25\",\"limit_price\":\"30000\"}},"
            + "\"created_time\":\"2026-02-08T00:00:00Z\"}}";

    CoinbaseOrderDetailResponse response =
        new ObjectMapper().readValue(json, CoinbaseOrderDetailResponse.class);
    Order adapted = CoinbaseAdapters.adaptOrder(response.getOrder());

    assertEquals(LimitOrder.class, adapted.getClass());
    assertEquals(new BigDecimal("30000"), ((LimitOrder) adapted).getLimitPrice());
  }

  @Test(expected = org.knowm.xchange.exceptions.NotAvailableFromExchangeException.class)
  public void testDeserializeCdeLimitFokOrderDetailWithoutFabricatingInstrument() throws Exception {
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

    assertNull(response.getOrder().getInstrument());

    CoinbaseAdapters.adaptOrder(response.getOrder());
  }

  @Test(expected = org.knowm.xchange.exceptions.NotAvailableFromExchangeException.class)
  public void testRejectQuoteSizedOrderWithoutBaseQuantityConversion() throws Exception {
    String json =
        "{\"order\":{\"order_id\":\"quote-1\",\"side\":\"BUY\",\"product_id\":\"BTC-USD\","
            + "\"status\":\"OPEN\",\"order_type\":\"MARKET\",\"time_in_force\":\"IOC\","
            + "\"size\":\"1000\",\"size_in_quote\":true,"
            + "\"order_configuration\":{\"market_market_ioc\":{"
            + "\"quote_size\":\"1000\"}},\"created_time\":\"2026-02-08T00:00:00Z\"}}";

    CoinbaseOrderDetailResponse response =
        new ObjectMapper().readValue(json, CoinbaseOrderDetailResponse.class);

    CoinbaseAdapters.adaptOrder(response.getOrder());
  }
}


