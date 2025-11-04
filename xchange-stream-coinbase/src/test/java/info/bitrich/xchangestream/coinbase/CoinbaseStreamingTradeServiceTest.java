package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseFuturesBalanceSummary;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseUserOrderEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoinbaseStreamingTradeServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void extractOrderEventsMapsFields() throws Exception {
    String payload =
        "{\n"
            + "  \"channel\": \"user\",\n"
            + "  \"events\": [\n"
            + "    {\n"
            + "      \"type\": \"snapshot\",\n"
            + "      \"orders\": [\n"
            + "        {\n"
            + "          \"order_id\": \"111\",\n"
            + "          \"client_order_id\": \"abc\",\n"
            + "          \"product_id\": \"BTC-USD\",\n"
            + "          \"order_side\": \"buy\",\n"
            + "          \"order_type\": \"limit\",\n"
            + "          \"limit_price\": \"200\",\n"
            + "          \"avg_price\": \"199.5\",\n"
            + "          \"size\": \"1.2\",\n"
            + "          \"cumulative_quantity\": \"0.2\",\n"
            + "          \"leaves_quantity\": \"1.0\",\n"
            + "          \"status\": \"OPEN\",\n"
            + "          \"event_time\": \"2024-01-02T00:00:00Z\"\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    JsonNode node = MAPPER.readTree(payload);
    CoinbaseStreamingTradeService service =
        new CoinbaseStreamingTradeService(null, null);
    Method method =
        CoinbaseStreamingTradeService.class.getDeclaredMethod(
            "extractOrderEvents", JsonNode.class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<CoinbaseUserOrderEvent> events =
        (List<CoinbaseUserOrderEvent>) method.invoke(service, node);

    assertEquals(1, events.size());
    CoinbaseUserOrderEvent event = events.get(0);
    assertEquals("111", event.getOrderId());
    assertEquals("abc", event.getClientOrderId());
    assertEquals(new BigDecimal("200"), event.getLimitPrice());
    assertEquals(new BigDecimal("1.2"), event.getSize());
    assertEquals("OPEN", event.getStatus());
    assertNotNull(event.getEventTime());
  }

  @Test
  void extractBalanceSummaryMapsNumericFields() throws Exception {
    String payload =
        "{\n"
            + "  \"channel\": \"futures_balance_summary\",\n"
            + "  \"events\": [\n"
            + "    {\n"
            + "      \"type\": \"snapshot\",\n"
            + "      \"fcm_balance_summary\": {\n"
            + "        \"futures_buying_power\": \"5000\",\n"
            + "        \"total_usd_balance\": \"8000\",\n"
            + "        \"unrealized_pnl\": \"12.5\",\n"
            + "        \"daily_realized_pnl\": \"1.2\",\n"
            + "        \"initial_margin\": \"200\",\n"
            + "        \"available_margin\": \"300\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    JsonNode node = MAPPER.readTree(payload);
    CoinbaseStreamingTradeService service =
        new CoinbaseStreamingTradeService(null, null);
    Method method =
        CoinbaseStreamingTradeService.class.getDeclaredMethod(
            "extractBalanceSummaries", JsonNode.class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<CoinbaseFuturesBalanceSummary> summaries =
        (List<CoinbaseFuturesBalanceSummary>) method.invoke(service, node);

    assertEquals(1, summaries.size());
    CoinbaseFuturesBalanceSummary summary = summaries.get(0);
    assertEquals(new BigDecimal("5000"), summary.getFuturesBuyingPower());
    assertEquals(new BigDecimal("8000"), summary.getTotalUsdBalance());
    assertEquals(new BigDecimal("12.5"), summary.getUnrealizedPnl());
  }
}

