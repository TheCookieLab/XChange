package info.bitrich.xchangestream.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * User order/trade adaptation, credential gating, and channel memoization for {@link
 * PolymarketStreamingTradeService}, all driven by scripted messages without a live WebSocket.
 */
class PolymarketStreamingTradeServiceTest {

  private static final String CONDITION_ID =
      "0x9b0f6b43e1a44c2fb2d3a1e5c7d8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6";
  private static final String TOKEN_ID =
      "104173557214744537570424345347209544585775842950109756851652855913015295508992";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID, Currency.USD);
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  private static final String ORDER_PLACEMENT =
      "{\"event_type\":\"order\",\"id\":\"order-1\",\"market\":\""
          + CONDITION_ID
          + "\",\"asset_id\":\""
          + TOKEN_ID
          + "\",\"side\":\"BUY\",\"original_size\":\"10\",\"size_matched\":\"4\","
          + "\"price\":\"0.56\",\"type\":\"PLACEMENT\",\"status\":\"LIVE\",\"order_type\":\"GTC\","
          + "\"created_at\":\"1669149841\",\"timestamp\":\"1669149841000\"}";

  private static final String TAKER_TRADE =
      "{\"event_type\":\"trade\",\"id\":\"trade-1\",\"taker_order_id\":\"order-9\",\"market\":\""
          + CONDITION_ID
          + "\",\"asset_id\":\""
          + TOKEN_ID
          + "\",\"side\":\"BUY\",\"size\":\"6\",\"fee_rate_bps\":\"0\",\"price\":\"0.56\","
          + "\"status\":\"MATCHED\",\"match_time\":\"1669149841\",\"trader_side\":\"TAKER\","
          + "\"timestamp\":\"1669149841000\",\"maker_orders\":[{"
          + "\"order_id\":\"order-7\",\"matched_amount\":\"6\",\"price\":\"0.56\","
          + "\"side\":\"SELL\"}]}";

  /** Feeds scripted messages through the user channel instead of a socket. */
  private static final class FakeService extends PolymarketStreamingService {
    private final List<JsonNode> scripted;
    private final List<String> subscriptions = new ArrayList<>();

    FakeService(List<JsonNode> scripted, boolean withCredentials) {
      super(
          "wss://stream.test/ws/user",
          withCredentials ? "key" : null,
          withCredentials ? "secret" : null,
          withCredentials ? "passphrase" : null);
      this.scripted = scripted;
    }

    @Override
    public Observable<JsonNode> subscribeChannel(String channelName, Object... args) {
      subscriptions.add(channelName + ":" + String.join(",", toStrings(args)));
      return Observable.fromIterable(scripted);
    }

    private static List<String> toStrings(Object... args) {
      List<String> out = new ArrayList<>();
      for (Object arg : args) {
        out.add(String.valueOf(arg));
      }
      return out;
    }
  }

  @Test
  void orderStreamAdaptsAPlacementToAPartiallyFilledBid() throws Exception {
    FakeService fake =
        new FakeService(List.of(MAPPER.readTree(ORDER_PLACEMENT), MAPPER.readTree(TAKER_TRADE)), true);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    List<Order> orders = service.getOrderChanges(CONTRACT).toList().blockingGet();

    // The trade event must not leak into the order stream.
    assertEquals(1, orders.size());
    LimitOrder order = (LimitOrder) orders.get(0);
    assertEquals("order-1", order.getId());
    assertEquals(OrderType.BID, order.getType(), "RULE_TOKEN_DIRECT: BUY on the token is a bid");
    assertEquals(CONTRACT, order.getInstrument());
    assertEquals(new BigDecimal("10"), order.getOriginalAmount());
    assertEquals(new BigDecimal("4"), order.getCumulativeAmount());
    assertEquals(new BigDecimal("0.56"), order.getLimitPrice());
    assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
    assertEquals(new Date(1669149841000L), order.getTimestamp());
    assertEquals(List.of("user:" + CONDITION_ID), fake.subscriptions);
  }

  @Test
  void orderStreamAppliesTheStatusTruthTable() throws Exception {
    List<JsonNode> script = new ArrayList<>();
    script.add(MAPPER.readTree(orderWithStatus("LIVE", "0")));
    script.add(MAPPER.readTree(orderWithStatus("LIVE", "3")));
    script.add(MAPPER.readTree(orderWithStatus("MATCHED", "10")));
    script.add(MAPPER.readTree(orderWithStatus("CANCELED", "2")));
    script.add(MAPPER.readTree(orderWithStatus("DELAYED", "0")));
    script.add(MAPPER.readTree(orderWithStatus("UNRECOGNIZED", "0")));
    FakeService fake = new FakeService(script, true);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    List<Order> orders = service.getOrderChanges(CONTRACT).toList().blockingGet();

    assertEquals(OrderStatus.OPEN, orders.get(0).getStatus());
    assertEquals(
        OrderStatus.PARTIALLY_FILLED, orders.get(1).getStatus(), "live with fills is partial");
    assertEquals(OrderStatus.FILLED, orders.get(2).getStatus());
    assertEquals(OrderStatus.CANCELED, orders.get(3).getStatus());
    assertEquals(OrderStatus.PENDING_NEW, orders.get(4).getStatus());
    assertEquals(OrderStatus.UNKNOWN, orders.get(5).getStatus());
  }

  @Test
  void takerTradeYieldsASingleFill() throws Exception {
    FakeService fake =
        new FakeService(List.of(MAPPER.readTree(ORDER_PLACEMENT), MAPPER.readTree(TAKER_TRADE)), true);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    List<UserTrade> fills = service.getUserTrades(CONTRACT).toList().blockingGet();

    // The order event must not leak into the trade stream.
    assertEquals(1, fills.size());
    UserTrade fill = fills.get(0);
    assertEquals("trade-1", fill.getId());
    assertEquals("order-9", fill.getOrderId(), "a taker fill belongs to the taker order");
    assertEquals(OrderType.BID, fill.getType());
    assertEquals(CONTRACT, fill.getInstrument());
    assertEquals(new BigDecimal("6"), fill.getOriginalAmount());
    assertEquals(new BigDecimal("0.56"), fill.getPrice());
    assertEquals(new Date(1669149841000L), fill.getTimestamp());
  }

  @Test
  void makerTradeYieldsOneFillPerMatchedMakerOrder() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    "{\"event_type\":\"trade\",\"id\":\"trade-2\",\"taker_order_id\":\"order-x\","
                        + "\"market\":\""
                        + CONDITION_ID
                        + "\",\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"side\":\"SELL\",\"size\":\"9\",\"fee_rate_bps\":\"0\","
                        + "\"price\":\"0.56\",\"status\":\"MATCHED\",\"match_time\":\"1669149842\","
                        + "\"trader_side\":\"MAKER\",\"timestamp\":\"1669149842000\","
                        + "\"maker_orders\":["
                        + "{\"order_id\":\"order-a\",\"matched_amount\":\"5\",\"price\":\"0.56\","
                        + "\"side\":\"BUY\"},"
                        + "{\"order_id\":\"order-b\",\"matched_amount\":\"4\",\"price\":\"0.55\","
                        + "\"side\":\"BUY\"}]}")),
            true);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    List<UserTrade> fills = service.getUserTrades(CONTRACT).toList().blockingGet();

    assertEquals(2, fills.size());
    UserTrade first = fills.get(0);
    assertEquals("trade-2", first.getId());
    assertEquals("order-a", first.getOrderId());
    assertEquals(OrderType.BID, first.getType(), "the maker order's own side drives the type");
    assertEquals(new BigDecimal("5"), first.getOriginalAmount());
    assertEquals(new BigDecimal("0.56"), first.getPrice());
    assertEquals(new Date(1669149842000L), first.getTimestamp());
    UserTrade second = fills.get(1);
    assertEquals("order-b", second.getOrderId());
    assertEquals(new BigDecimal("4"), second.getOriginalAmount());
    assertEquals(new BigDecimal("0.55"), second.getPrice());
  }

  @Test
  void unknownTraderSideTerminatesTheStream() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    "{\"event_type\":\"trade\",\"id\":\"trade-3\",\"taker_order_id\":\"order-x\","
                        + "\"market\":\""
                        + CONDITION_ID
                        + "\",\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"side\":\"BUY\",\"size\":\"1\",\"price\":\"0.56\","
                        + "\"match_time\":\"1669149843\",\"trader_side\":\"ARBITER\","
                        + "\"timestamp\":\"1669149843000\"}")),
            true);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    service
        .getUserTrades(CONTRACT)
        .test()
        .assertError(ExchangeException.class)
        .assertError(
            error ->
                "Polymarket user trade has unrecognized trader_side: ARBITER"
                    .equals(error.getMessage()));
  }

  @Test
  void userStreamsRequireCredentials() {
    FakeService fake = new FakeService(List.of(), false);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    ExchangeSecurityException tradesError =
        assertThrows(ExchangeSecurityException.class, () -> service.getUserTrades(CONTRACT));
    assertTrue(tradesError.getMessage().contains("apiKey"));
    assertThrows(ExchangeSecurityException.class, () -> service.getOrderChanges(CONTRACT));
    assertTrue(fake.subscriptions.isEmpty(), "no subscription may be attempted without credentials");
  }

  @Test
  void userChannelIsSubscribedOncePerMarketAcrossStreams() {
    FakeService fake = new FakeService(List.of(), true);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    service.getUserTrades(CONTRACT).subscribe(ignored -> {}, error -> {});
    service.getOrderChanges(CONTRACT).subscribe(ignored -> {}, error -> {});

    // Order and trade events share one user-channel subscription per condition id; the base
    // streaming service would orphan any second subscriber.
    assertEquals(List.of("user:" + CONDITION_ID), fake.subscriptions);
  }

  @Test
  void orderStreamLeavesAnUnfilledLiveOrderWithoutCumulativeAmount() throws Exception {
    FakeService fake =
        new FakeService(List.of(MAPPER.readTree(orderWithStatus("LIVE", "0"))), true);
    PolymarketStreamingTradeService service = new PolymarketStreamingTradeService(fake);

    LimitOrder order = (LimitOrder) service.getOrderChanges(CONTRACT).blockingFirst();
    assertNull(order.getCumulativeAmount(), "no fills means no cumulative amount");
    assertEquals(OrderStatus.OPEN, order.getStatus());
  }

  private static String orderWithStatus(String status, String sizeMatched) {
    return "{\"event_type\":\"order\",\"id\":\"order-s\",\"market\":\""
        + CONDITION_ID
        + "\",\"asset_id\":\""
        + TOKEN_ID
        + "\",\"side\":\"SELL\",\"original_size\":\"10\",\"size_matched\":\""
        + sizeMatched
        + "\",\"price\":\"0.60\",\"type\":\"UPDATE\",\"status\":\""
        + status
        + "\",\"order_type\":\"GTC\",\"created_at\":\"1669149841\","
        + "\"timestamp\":\"1669149841000\"}";
  }
}
