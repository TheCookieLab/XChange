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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CoinbaseStreamingTradeServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void getUserOrderEventsMapsFields() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        String payload = "{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"snapshot\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"111\",\n" + "          \"client_order_id\": \"abc\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"order_type\": \"limit\",\n" + "          \"limit_price\": \"200\",\n" + "          \"avg_price\": \"199.5\",\n" + "          \"size\": \"1.2\",\n" + "          \"cumulative_quantity\": \"0.2\",\n" + "          \"leaves_quantity\": \"1.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-02T00:00:00Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}";

        JsonNode node = MAPPER.readTree(payload);
        StubStreamingService streamingService = new StubStreamingService(Observable.just(node));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<CoinbaseUserOrderEvent> events = service.getUserOrderEvents(Collections.emptyList()).toList().blockingGet();

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
    void getFuturesBalanceSummaryMapsNumericFields() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        String payload = "{\n" + "  \"channel\": \"futures_balance_summary\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"snapshot\",\n" + "      \"fcm_balance_summary\": {\n" + "        \"futures_buying_power\": \"5000\",\n" + "        \"total_usd_balance\": \"8000\",\n" + "        \"unrealized_pnl\": \"12.5\",\n" + "        \"daily_realized_pnl\": \"1.2\",\n" + "        \"initial_margin\": \"200\",\n" + "        \"available_margin\": \"300\"\n" + "      }\n" + "    }\n" + "  ]\n" + "}";

        JsonNode node = MAPPER.readTree(payload);
        StubStreamingService streamingService = new StubStreamingService(Observable.just(node));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<CoinbaseFuturesBalanceSummary> summaries = service.getFuturesBalanceSummary().toList().blockingGet();

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

    @Test
    void getUserTradesHandlesConcurrentEventsAtomically() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        // Create multiple events for the same orderId with sequential cumulative quantities
        // These will be processed concurrently to test the race condition fix
        JsonNode message1 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"concurrent-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"10.0\",\n" + "          \"cumulative_quantity\": \"1.0\",\n" + "          \"leaves_quantity\": \"9.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        JsonNode message2 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"concurrent-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"10.0\",\n" + "          \"cumulative_quantity\": \"2.0\",\n" + "          \"leaves_quantity\": \"8.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        JsonNode message3 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"concurrent-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"10.0\",\n" + "          \"cumulative_quantity\": \"3.0\",\n" + "          \"leaves_quantity\": \"7.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Create a streaming service that emits all messages concurrently
        // This simulates the race condition where multiple events for the same orderId
        // arrive at nearly the same time
        StubStreamingService streamingService = new StubStreamingService(
            Observable.just(message1, message2, message3)
                .flatMap(msg -> Observable.just(msg).delay(1, TimeUnit.MILLISECONDS))
        );
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<UserTrade> trades = service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

        // Should emit exactly 3 trades (one for each incremental fill)
        // With the atomic fix, even if events are processed concurrently, each delta
        // should be calculated correctly based on the previous value
        assertEquals(3, trades.size(), "Should emit all incremental fills correctly");

        // Verify all trades are for the same order
        assertTrue(trades.stream().allMatch(t -> "concurrent-order".equals(t.getOrderId())),
            "All trades should be for the same order");

        // Calculate total amount - should equal final cumulative quantity (3.0)
        BigDecimal totalAmount = trades.stream()
            .map(UserTrade::getOriginalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("3.0"), totalAmount,
            "Total amount should equal final cumulative quantity, indicating correct delta calculation");

        // Verify each trade has a positive delta
        assertTrue(trades.stream().allMatch(t -> t.getOriginalAmount().compareTo(BigDecimal.ZERO) > 0),
            "All trades should have positive amounts");
    }

    @Test
    void getUserTradesFiltersZeroDeltaEvents() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        // First event with cumulative quantity
        JsonNode message1 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"zero-delta-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"0.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Second event with same cumulative quantity (zero delta - should be filtered)
        JsonNode message2 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"zero-delta-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.5\",\n" + "          \"leaves_quantity\": \"0.5\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Third event with increasing cumulative quantity (should be emitted)
        JsonNode message3 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"zero-delta-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"1.0\",\n" + "          \"cumulative_quantity\": \"0.8\",\n" + "          \"leaves_quantity\": \"0.2\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:03Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        StubStreamingService streamingService = new StubStreamingService(Observable.just(message1, message2, message3));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<UserTrade> trades = service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

        // Should only emit 2 trades (first and third, second filtered out due to zero delta)
        assertEquals(2, trades.size(), "Should filter out zero-delta events");

        assertEquals(new BigDecimal("0.5"), trades.get(0).getOriginalAmount(),
            "First event should emit full cumulative amount");
        assertEquals(new BigDecimal("0.3"), trades.get(1).getOriginalAmount(),
            "Third event should emit delta: 0.8 - 0.5 = 0.3");
    }

    @Test
    void getUserTradesPreventsDuplicateFillsFromRaceCondition() throws Exception {
        ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
        spec.setApiKey("key");
        spec.setSecretKey("secret");

        // Simulate a scenario where multiple events with the same cumulative quantity
        // arrive concurrently - should only emit one trade
        JsonNode message1 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"race-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"5.0\",\n" + "          \"cumulative_quantity\": \"1.0\",\n" + "          \"leaves_quantity\": \"4.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:01Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        // Two events with the same cumulative quantity arriving concurrently
        // With the atomic fix, both should see the same previous value and only one
        // should emit a trade (the other will have zero delta)
        JsonNode message2 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"race-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"5.0\",\n" + "          \"cumulative_quantity\": \"2.0\",\n" + "          \"leaves_quantity\": \"3.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        JsonNode message3 = MAPPER.readTree("{\n" + "  \"channel\": \"user\",\n" + "  \"events\": [\n" + "    {\n" + "      \"type\": \"update\",\n" + "      \"orders\": [\n" + "        {\n" + "          \"order_id\": \"race-order\",\n" + "          \"product_id\": \"BTC-USD\",\n" + "          \"order_side\": \"buy\",\n" + "          \"avg_price\": \"50000\",\n" + "          \"size\": \"5.0\",\n" + "          \"cumulative_quantity\": \"2.0\",\n" + "          \"leaves_quantity\": \"3.0\",\n" + "          \"status\": \"OPEN\",\n" + "          \"event_time\": \"2024-01-01T00:00:02Z\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}");

        StubStreamingService streamingService = new StubStreamingService(Observable.just(message1, message2, message3));
        CoinbaseStreamingTradeService service = new CoinbaseStreamingTradeService(streamingService, spec);

        List<UserTrade> trades = service.getUserTrades(CurrencyPair.BTC_USD).toList().blockingGet();

        // Should emit exactly 2 trades:
        // 1. First event: 1.0 (first cumulative)
        // 2. Second or third event: 1.0 delta (2.0 - 1.0)
        // The other duplicate event should be filtered out (zero delta)
        assertEquals(2, trades.size(),
            "Should prevent duplicate fills even when events arrive concurrently");

        BigDecimal totalAmount = trades.stream()
            .map(UserTrade::getOriginalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("2.0"), totalAmount,
            "Total should equal final cumulative quantity, preventing duplicate fills");
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
