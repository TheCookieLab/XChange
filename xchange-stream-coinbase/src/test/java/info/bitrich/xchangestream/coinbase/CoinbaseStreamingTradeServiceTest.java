package info.bitrich.xchangestream.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseFuturesBalanceSummary;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseUserOrderEvent;
import io.reactivex.rxjava3.core.Observable;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoinbaseStreamingTradeServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void extractOrderEventsMapsFields() throws Exception {
        String payload = "{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"snapshot\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"111\",\n" + "          \"client_order_id\": \"abc\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"order_type\": \"limit\",\n" + "          \"limit_price\": \"200\",\n" + "          \"avg_price\": \"199.5\",\n" + "          \"size\": \"1.2\",\n" + "          \"cumulative_quantity\": \"0.2\",\n" + "          \"leaves_quantity\": \"1.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-02T00:00:00Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}";

        JsonNode node = MAPPER.readTree(payload);
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(null, null);
        Method method = CoinbaseStreamingTradeService.class.getDeclaredMethod("extractOrderEvents", JsonNode.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked") List<CoinbaseUserOrderEvent> events = (List<CoinbaseUserOrderEvent>) method.invoke(service, node);

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
        String payload = "{\n" + "  \"channel\": \"futures_balance_summary\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"snapshot\",\n" + "      \"fcm_balance_summary\": {\n" + "        \"futures_buying_power\": \"5000\",\n" + "        \"total_usd_balance\": \"8000\",\n" + "        \"unrealized_pnl\": \"12.5\",\n" + "        \"daily_realized_pnl\": \"1.2\",\n" + "        \"initial_margin\": \"200\",\n" + "        \"available_margin\": \"300\"\n" + "      }\n" + "    }\n" + "  ]\n" + "}";

        JsonNode node = MAPPER.readTree(payload);
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(null, null);
        Method method = CoinbaseStreamingTradeService.class.getDeclaredMethod("extractBalanceSummaries", JsonNode.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked") List<CoinbaseFuturesBalanceSummary> summaries = (List<CoinbaseFuturesBalanceSummary>) method.invoke(service, node);

        assertEquals(1, summaries.size());
        CoinbaseFuturesBalanceSummary summary = summaries.get(0);
        assertEquals(new BigDecimal("5000"), summary.getFuturesBuyingPower());
        assertEquals(new BigDecimal("8000"), summary.getTotalUsdBalance());
        assertEquals(new BigDecimal("12.5"), summary.getUnrealizedPnl());
        assertEquals(new BigDecimal("1.2"), summary.getDailyRealizedPnl());
        assertEquals(new BigDecimal("200"), summary.getInitialMargin());
        assertEquals(new BigDecimal("300"), summary.getAvailableMargin());
    }

    @Test
    void getUserTradesWithoutInstrumentSubscribesToAllProducts() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        JsonNode message = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"snapshot\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"filled-btc\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"101\",\n" + "          \"size\": \"2\",\n" + "          \"cumulative_quantity\": \"0.75\",\n" + "          \"leaves_quantity\": \"1.25\",\n" + "          \"status\": \"FILLED\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        },\n" + "        {\n" + "          \"order_id\": \"empty-fill\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"limit_price\": \"99\",\n" + "          \"size\": \"1\",\n" + "          \"cumulative_quantity\": \"0\",\n" + "          \"leaves_quantity\": \"1\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        },\n" + "        {\n" + "          \"order_id\": \"filled-eth\",\n" + "          \"product_id\": \"ETH-USD\",\n" + "          \"order_side\": \"sell\",\n" + "          \"avg_price\": \"2000\",\n" + "          \"size\": \"3\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"2.5\",\n" + "          \"status\": \"FILLED\",\n" + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        StubStreamingService streamingService = new StubStreamingService(Observable.just(message));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

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

        JsonNode message = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"snapshot\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"filled-btc\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"101\",\n" + "          \"size\": \"2\",\n" + "          \"cumulative_quantity\": \"0.75\",\n" + "          \"leaves_quantity\": \"1.25\",\n" + "          \"status\": \"FILLED\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        },\n" + "        {\n" + "          \"order_id\": \"filled-eth\",\n" + "          \"product_id\": \"ETH-USD\",\n" + "          \"order_side\": \"sell\",\n" + "          \"avg_price\": \"2000\",\n" + "          \"size\": \"3\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"2.5\",\n" + "          \"status\": \"FILLED\",\n" + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        StubStreamingService streamingService = new StubStreamingService(Observable.just(message));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<UserTrade> trades = service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

        assertEquals(1, trades.size());
        UserTrade trade = trades.get(0);
        assertEquals("filled-btc", trade.getOrderId());
        assertEquals(CurrencyPair.BTC_USD, trade.getInstrument());

        CoinbaseSubscriptionRequest request = streamingService.lastRequest();
        assertEquals(1, request.getProductIds().size());
        assertEquals("BTC-USD", request.getProductIds().get(0));
    }

    @Test
    void getUserTradesEmitsDeltaForPartialFills() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        // Simulate multiple updates for the same order with increasing cumulative quantity
        JsonNode message1 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"order_type\": \"limit\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"2.0\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"1.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        JsonNode message2 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"order_type\": \"limit\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"2.0\",\n" + "          \"cumulative_quantity\": \"1.0\",\n" + "          \"leaves_quantity\": \"1.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        JsonNode message3 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"order_type\": \"limit\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"2.0\",\n" + "          \"cumulative_quantity\": \"1.5\",\n" + "          \"leaves_quantity\": \"0.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Also include a duplicate update (same cumulative quantity) that should be filtered out
        JsonNode message4 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"order_type\": \"limit\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"2.0\",\n" + "          \"cumulative_quantity\": \"1.5\",\n" + "          \"leaves_quantity\": \"0.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:04Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        StubStreamingService streamingService = new StubStreamingService(Observable.just(message1, message2, message3, message4));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<UserTrade> trades = service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

        // Should emit 3 trades (one for each partial fill, duplicate filtered out)
        assertEquals(3, trades.size(), "Should emit trades for each incremental fill");

        // First trade: 0.5 BTC (first cumulative quantity)
        UserTrade first = trades.get(0);
        assertEquals("order-123", first.getOrderId());
        assertEquals(new BigDecimal("0.5"), first.getOriginalAmount(), "First event should emit full cumulative amount");

        // Second trade: 0.5 BTC (delta: 1.0 - 0.5 = 0.5)
        UserTrade second = trades.get(1);
        assertEquals("order-123", second.getOrderId());
        assertEquals(new BigDecimal("0.5"), second.getOriginalAmount(), "Second event should emit delta only");

        // Third trade: 0.5 BTC (delta: 1.5 - 1.0 = 0.5)
        UserTrade third = trades.get(2);
        assertEquals("order-123", third.getOrderId());
        assertEquals(new BigDecimal("0.5"), third.getOriginalAmount(), "Third event should emit delta only");

        // Total should be 1.5 BTC (not 4.5 BTC which would be the buggy behavior)
        BigDecimal totalAmount = trades.stream().map(UserTrade::getOriginalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("1.5"), totalAmount, "Total should match final cumulative quantity");
    }

    @Test
    void getUserTradesTracksMultipleOrdersSeparately() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        // Two different orders with overlapping updates
        JsonNode message1 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-1\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.3\",\n" + "          \"leaves_quantity\": \"0.7\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        },\n" + "        {\n" + "          \"order_id\": \"order-2\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"sell\",\n" + "          \"avg_price\": \"51000\",\n" + "          \"size\": \"2.0\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"1.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        JsonNode message2 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-1\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.6\",\n" + "          \"leaves_quantity\": \"0.4\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        },\n" + "        {\n" + "          \"order_id\": \"order-2\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"sell\",\n" + "          \"avg_price\": \"51000\",\n" + "          \"size\": \"2.0\",\n" + "          \"cumulative_quantity\": \"1.0\",\n" + "          \"leaves_quantity\": \"1.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        StubStreamingService streamingService = new StubStreamingService(Observable.just(message1, message2));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<UserTrade> trades = service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

        // Should have 4 trades total (2 orders × 2 updates each)
        assertEquals(4, trades.size(), "Should track both orders separately");

        // Verify order-1 trades
        List<UserTrade> order1Trades = trades.stream().filter(t -> "order-1".equals(t.getOrderId())).collect(java.util.stream.Collectors.toList());
        assertEquals(2, order1Trades.size());
        assertEquals(new BigDecimal("0.3"), order1Trades.get(0).getOriginalAmount());
        assertEquals(new BigDecimal("0.3"), order1Trades.get(1).getOriginalAmount()); // delta: 0.6 - 0.3

        // Verify order-2 trades
        List<UserTrade> order2Trades = trades.stream().filter(t -> "order-2".equals(t.getOrderId())).collect(java.util.stream.Collectors.toList());
        assertEquals(2, order2Trades.size());
        assertEquals(new BigDecimal("0.5"), order2Trades.get(0).getOriginalAmount());
        assertEquals(new BigDecimal("0.5"), order2Trades.get(1).getOriginalAmount()); // delta: 1.0 - 0.5
    }

    @Test
    void getUserTradesFiltersOutNonIncreasingCumulativeQuantity() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        // First update with cumulative quantity
        JsonNode message1 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"0.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Second update with same cumulative quantity (should be filtered out)
        JsonNode message2 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"0.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Third update with decreasing cumulative quantity (should be filtered out)
        JsonNode message3 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.3\",\n" + "          \"leaves_quantity\": \"0.7\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Fourth update with increasing cumulative quantity (should be emitted)
        JsonNode message4 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"order-123\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.7\",\n" + "          \"leaves_quantity\": \"0.3\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:04Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        StubStreamingService streamingService = new StubStreamingService(Observable.just(message1, message2, message3, message4));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<UserTrade> trades = service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

        // Should only emit 2 trades (first and fourth, second and third filtered out)
        assertEquals(2, trades.size(), "Should filter out non-increasing cumulative quantities");

        assertEquals(new BigDecimal("0.5"), trades.get(0).getOriginalAmount());
        assertEquals(new BigDecimal("0.2"), trades.get(1).getOriginalAmount()); // delta: 0.7 - 0.5
    }

    private static final class StubStreamingService extends CoinbaseStreamingService {
        private final Observable<JsonNode> response;
        private CoinbaseSubscriptionRequest lastRequest;

        StubStreamingService(Observable<JsonNode> response) {
            super("wss://example.com", () -> null, 8, 750);
            this.response = response;
        }

        @Override
        protected Observable<JsonNode> observeChannel(CoinbaseSubscriptionRequest request) {
            this.lastRequest = request;
            return response;
        }

        CoinbaseSubscriptionRequest lastRequest() {
            return lastRequest;
        }
    }
}
