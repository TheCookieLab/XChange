package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseFuturesBalanceSummary;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseUserOrderEvent;
import io.reactivex.rxjava3.core.Observable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;

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

  @Test
  void getUserTradesWithoutInstrumentSubscribesToAllProducts() throws Exception {
    ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
    spec.setApiKey("key");
    spec.setSecretKey("secret");

    JsonNode message =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"user\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"orders\": [\n"
                + "        {\n"
                + "          \"order_id\": \"filled-btc\",\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"order_side\": \"buy\",\n"
                + "          \"avg_price\": \"101\",\n"
                + "          \"size\": \"2\",\n"
                + "          \"cumulative_quantity\": \"0.75\",\n"
                + "          \"leaves_quantity\": \"1.25\",\n"
                + "          \"status\": \"FILLED\",\n"
                + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"order_id\": \"empty-fill\",\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"order_side\": \"buy\",\n"
                + "          \"limit_price\": \"99\",\n"
                + "          \"size\": \"1\",\n"
                + "          \"cumulative_quantity\": \"0\",\n"
                + "          \"leaves_quantity\": \"1\",\n"
                + "          \"status\": \"OPEN\",\n"
                + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"order_id\": \"filled-eth\",\n"
                + "          \"product_id\": \"ETH-USD\",\n"
                + "          \"order_side\": \"sell\",\n"
                + "          \"avg_price\": \"2000\",\n"
                + "          \"size\": \"3\",\n"
                + "          \"cumulative_quantity\": \"0.5\",\n"
                + "          \"leaves_quantity\": \"2.5\",\n"
                + "          \"status\": \"FILLED\",\n"
                + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    StubStreamingService streamingService =
        new StubStreamingService(Observable.just(message));
    CoinbaseStreamingTradeService service =
        new CoinbaseStreamingTradeService(streamingService, spec);

    List<UserTrade> trades = service.getUserTrades().toList().blockingGet();

    assertEquals(2, trades.size());
    UserTrade first = trades.get(0);
    assertEquals("filled-btc", first.getOrderId());
    assertEquals(CurrencyPair.BTC_USD, first.getInstrument());
    assertEquals(new BigDecimal("101"), first.getPrice());
    assertEquals(new BigDecimal("0.75"), first.getOriginalAmount());
    UserTrade second = trades.get(1);
    assertEquals("filled-eth", second.getOrderId());
    assertEquals(CurrencyPair.ETH_USD, second.getInstrument());
    assertEquals(new BigDecimal("2000"), second.getPrice());
    assertEquals(new BigDecimal("0.5"), second.getOriginalAmount());

    CoinbaseSubscriptionRequest request = streamingService.lastRequest();
    assertEquals(CoinbaseChannel.USER, request.getChannel());
    assertTrue(request.getProductIds().isEmpty());
  }

  @Test
  void getUserTradesFiltersByCurrencyPair() throws Exception {
    ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
    spec.setApiKey("key");
    spec.setSecretKey("secret");

    JsonNode message =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"user\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"orders\": [\n"
                + "        {\n"
                + "          \"order_id\": \"filled-btc\",\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"order_side\": \"buy\",\n"
                + "          \"avg_price\": \"101\",\n"
                + "          \"size\": \"2\",\n"
                + "          \"cumulative_quantity\": \"0.75\",\n"
                + "          \"leaves_quantity\": \"1.25\",\n"
                + "          \"status\": \"FILLED\",\n"
                + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"order_id\": \"filled-eth\",\n"
                + "          \"product_id\": \"ETH-USD\",\n"
                + "          \"order_side\": \"sell\",\n"
                + "          \"avg_price\": \"2000\",\n"
                + "          \"size\": \"3\",\n"
                + "          \"cumulative_quantity\": \"0.5\",\n"
                + "          \"leaves_quantity\": \"2.5\",\n"
                + "          \"status\": \"FILLED\",\n"
                + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    StubStreamingService streamingService =
        new StubStreamingService(Observable.just(message));
    CoinbaseStreamingTradeService service =
        new CoinbaseStreamingTradeService(streamingService, spec);

    List<UserTrade> trades =
        service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

    assertEquals(1, trades.size());
    UserTrade trade = trades.get(0);
    assertEquals("filled-btc", trade.getOrderId());
    assertEquals(CurrencyPair.BTC_USD, trade.getInstrument());

    CoinbaseSubscriptionRequest request = streamingService.lastRequest();
    assertEquals(1, request.getProductIds().size());
    assertEquals("BTC-USD", request.getProductIds().get(0));
  }

  private static final class StubStreamingService extends CoinbaseStreamingService {
    private final Observable<JsonNode> response;
    private CoinbaseSubscriptionRequest lastRequest;

    StubStreamingService(Observable<JsonNode> response) {
      super("wss://example.com", () -> null, 8, 750);
      this.response = response;
    }

    @Override
    Observable<JsonNode> observeChannel(CoinbaseSubscriptionRequest request) {
      this.lastRequest = request;
      return response;
    }

    CoinbaseSubscriptionRequest lastRequest() {
      return lastRequest;
    }
  }
}
