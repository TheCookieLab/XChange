package info.bitrich.xchangestream.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/**
 * Deterministic protocol tests for {@link PolymarketStreamingService}: initial-versus-dynamic
 * subscribe frame shapes, credential gating, payload-derived routing, batched price-change
 * fan-out, the connection-bound writer heartbeat, and reconnect-style resubscription — all without
 * a live WebSocket.
 */
class PolymarketStreamingServiceTest {

  private static final String WS_URL = "wss://stream.test/ws/market";
  private static final String CONDITION_ID =
      "0x9b0f6b43e1a44c2fb2d3a1e5c7d8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6";
  private static final String TOKEN_ID =
      "104173557214744537570424345347209544585775842950109756851652855913015295508992";
  private static final String TOKEN_ID_B =
      "71321045679252212594626385532706912750332728571942532289631379312455583992563";
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  /** Records outbound frames and counts JSON dispatches instead of writing to a socket. */
  private static final class CapturingService extends PolymarketStreamingService {
    private final List<String> sent = new ArrayList<>();
    private int parsedMessages;

    CapturingService(String apiKey, String secret, String passphrase) {
      super(WS_URL, apiKey, secret, passphrase);
    }

    CapturingService(
        String apiUrl,
        String apiKey,
        String secret,
        String passphrase,
        Scheduler heartbeatScheduler,
        long heartbeatIntervalSeconds) {
      super(apiUrl, apiKey, secret, passphrase, heartbeatScheduler, heartbeatIntervalSeconds);
    }

    @Override
    public void sendMessage(String message) {
      if (message != null) {
        sent.add(message);
      }
    }

    @Override
    protected void handleMessage(JsonNode message) {
      parsedMessages++;
      super.handleMessage(message);
    }
  }

  /** Records per-channel dispatches instead of delivering to subscribers. */
  private static final class DispatchingService extends PolymarketStreamingService {
    private final List<String> channels = new ArrayList<>();
    private final List<JsonNode> messages = new ArrayList<>();

    DispatchingService() {
      super(WS_URL, null, null, null);
    }

    @Override
    protected void handleChannelMessage(String channel, JsonNode message) {
      channels.add(channel);
      messages.add(message);
    }
  }

  @Test
  void marketSubscribeFrameCarriesTheTokenIds() throws Exception {
    CapturingService service = new CapturingService(null, null, null);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID);

    assertEquals(1, service.sent.size());
    JsonNode frame = MAPPER.readTree(service.sent.get(0));
    assertEquals("market", frame.path("type").asText());
    assertTrue(
        frame.path("operation").isMissingNode(), "the first frame is the initial subscription");
    assertEquals(TOKEN_ID, frame.path("assets_ids").get(0).asText());
    assertTrue(frame.path("auth").isMissingNode(), "market subscriptions carry no auth");
  }

  @Test
  void userSubscribeFrameCarriesTheL2AuthAndConditionIds() throws Exception {
    CapturingService service = new CapturingService("key", "secret", "passphrase");
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_USER, CONDITION_ID);

    assertEquals(1, service.sent.size());
    JsonNode frame = MAPPER.readTree(service.sent.get(0));
    assertEquals("user", frame.path("type").asText());
    assertTrue(
        frame.path("operation").isMissingNode(), "the first frame is the initial subscription");
    assertEquals(CONDITION_ID, frame.path("markets").get(0).asText());
    JsonNode auth = frame.path("auth");
    assertEquals("key", auth.path("apiKey").asText());
    assertEquals("secret", auth.path("secret").asText());
    assertEquals("passphrase", auth.path("passphrase").asText());
  }

  @Test
  void userSubscribeWithoutCredentialsFailsBeforeAnyFrame() {
    CapturingService service = new CapturingService(null, null, null);
    assertThrows(
        ExchangeSecurityException.class,
        () -> service.getSubscribeMessage(PolymarketStreamingService.CHANNEL_USER, CONDITION_ID));
    assertTrue(service.sent.isEmpty());
    assertFalse(service.hasCredentials());
    assertFalse(new PolymarketStreamingService(WS_URL, " ", "secret", "pass").hasCredentials());
  }

  @Test
  void laterSubscriptionsUseDynamicUpdateFrames() throws Exception {
    CapturingService service = new CapturingService(null, null, null);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID_B);

    assertEquals(2, service.sent.size());
    JsonNode first = MAPPER.readTree(service.sent.get(0));
    assertEquals("market", first.path("type").asText());
    assertEquals(TOKEN_ID, first.path("assets_ids").get(0).asText());

    JsonNode second = MAPPER.readTree(service.sent.get(1));
    assertEquals(
        "subscribe",
        second.path("operation").asText(),
        "every later subscription is a dynamic update frame");
    assertTrue(second.path("type").isMissingNode(), "update frames carry no type");
    assertEquals(TOKEN_ID_B, second.path("assets_ids").get(0).asText());
  }

  @Test
  void userChannelFollowsTheSameInitialThenUpdateShape() throws Exception {
    CapturingService service = new CapturingService("key", "secret", "passphrase");
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_USER, CONDITION_ID);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_USER, CONDITION_ID + "2");

    JsonNode first = MAPPER.readTree(service.sent.get(0));
    assertEquals("user", first.path("type").asText());
    assertTrue(first.path("auth").isObject());
    assertTrue(first.path("operation").isMissingNode());

    JsonNode second = MAPPER.readTree(service.sent.get(1));
    assertEquals("subscribe", second.path("operation").asText());
    assertTrue(second.path("type").isMissingNode(), "later user subscriptions are updates");
    assertTrue(second.path("auth").isMissingNode(), "dynamic updates carry no auth");
    assertEquals(CONDITION_ID + "2", second.path("markets").get(0).asText());
  }

  @Test
  void marketUnsubscribeFrameCarriesTheTokenIdsAndOperation() throws Exception {
    CapturingService service = new CapturingService(null, null, null);
    JsonNode frame =
        MAPPER.readTree(
            service.getUnsubscribeMessage(PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID));
    assertEquals("unsubscribe", frame.path("operation").asText());
    assertEquals(TOKEN_ID, frame.path("assets_ids").get(0).asText());
  }

  @Test
  void userUnsubscribeFrameCarriesTheConditionIdsAndOperation() throws Exception {
    CapturingService service = new CapturingService("key", "secret", "passphrase");
    JsonNode frame =
        MAPPER.readTree(
            service.getUnsubscribeMessage(PolymarketStreamingService.CHANNEL_USER, CONDITION_ID));
    assertEquals("unsubscribe", frame.path("operation").asText());
    assertEquals(CONDITION_ID, frame.path("markets").get(0).asText());
  }

  @Test
  void routingDerivesTheChannelFromThePayload() throws Exception {
    CapturingService service = new CapturingService(null, null, null);

    assertEquals(
        "market_" + TOKEN_ID,
        service.getChannelNameFromMessage(
            MAPPER.readTree(
                "{\"event_type\":\"book\",\"asset_id\":\"" + TOKEN_ID + "\"}")));
    assertEquals(
        "market_" + TOKEN_ID,
        service.getChannelNameFromMessage(
            MAPPER.readTree(
                "{\"event_type\":\"last_trade_price\",\"asset_id\":\"" + TOKEN_ID + "\"}")));
    assertEquals(
        "market_" + TOKEN_ID,
        service.getChannelNameFromMessage(
            MAPPER.readTree(
                "{\"event_type\":\"tick_size_change\",\"asset_id\":\"" + TOKEN_ID + "\"}")));
    assertEquals(
        "market_" + TOKEN_ID,
        service.getChannelNameFromMessage(
            MAPPER.readTree(
                "{\"event_type\":\"price_change\",\"price_changes\":[{\"asset_id\":\""
                    + TOKEN_ID
                    + "\"}]}")));
    assertEquals(
        "user_" + CONDITION_ID,
        service.getChannelNameFromMessage(
            MAPPER.readTree(
                "{\"event_type\":\"order\",\"market\":\"" + CONDITION_ID + "\"}")));
    assertEquals(
        "user_" + CONDITION_ID,
        service.getChannelNameFromMessage(
            MAPPER.readTree(
                "{\"event_type\":\"trade\",\"market\":\"" + CONDITION_ID + "\"}")));

    // Unroutable payloads: empty change list, missing ids, and unknown event types.
    assertNull(
        service.getChannelNameFromMessage(
            MAPPER.readTree("{\"event_type\":\"price_change\",\"price_changes\":[]}")));
    assertNull(
        service.getChannelNameFromMessage(MAPPER.readTree("{\"event_type\":\"book\"}")));
    assertNull(
        service.getChannelNameFromMessage(MAPPER.readTree("{\"event_type\":\"mystery\"}")));
  }

  @Test
  void batchedPriceChangeIsSplitPerAssetAndRoutedToEachMarketChannel() throws Exception {
    DispatchingService service = new DispatchingService();
    service.messageHandler(
        "{\"event_type\":\"price_change\",\"market\":\""
            + CONDITION_ID
            + "\",\"timestamp\":\"1669149842000\",\"price_changes\":["
            + "{\"asset_id\":\""
            + TOKEN_ID
            + "\",\"price\":\"0.44\",\"size\":\"0\",\"side\":\"BUY\",\"hash\":\"0xb\","
            + "\"best_bid\":\"0.40\",\"best_ask\":\"0.56\"},"
            + "{\"asset_id\":\""
            + TOKEN_ID_B
            + "\",\"price\":\"0.58\",\"size\":\"75\",\"side\":\"SELL\",\"hash\":\"0xc\","
            + "\"best_bid\":\"0.50\",\"best_ask\":\"0.60\"}]}");

    assertEquals(
        List.of("market_" + TOKEN_ID, "market_" + TOKEN_ID_B),
        service.channels,
        "each asset of a batched event is routed to its own channel");
    JsonNode first = service.messages.get(0);
    assertEquals(1, first.path("price_changes").size(), "each dispatch carries only its own change");
    assertEquals(TOKEN_ID, first.path("price_changes").get(0).path("asset_id").asText());
    assertEquals(CONDITION_ID, first.path("market").asText(), "top-level fields are preserved");
    assertEquals("1669149842000", first.path("timestamp").asText());
    JsonNode second = service.messages.get(1);
    assertEquals(1, second.path("price_changes").size());
    assertEquals(TOKEN_ID_B, second.path("price_changes").get(0).path("asset_id").asText());
  }

  @Test
  void singleAssetPriceChangeKeepsSingleChannelRouting() throws Exception {
    DispatchingService service = new DispatchingService();
    service.messageHandler(
        "{\"event_type\":\"price_change\",\"market\":\""
            + CONDITION_ID
            + "\",\"timestamp\":\"1669149842000\",\"price_changes\":[{"
            + "\"asset_id\":\""
            + TOKEN_ID
            + "\",\"price\":\"0.44\",\"size\":\"10\",\"side\":\"BUY\",\"hash\":\"0xb\"}]}");
    assertEquals(List.of("market_" + TOKEN_ID), service.channels);
    assertEquals(1, service.messages.get(0).path("price_changes").size());

    // Non-price-change events keep the whole-node routing.
    DispatchingService bookService = new DispatchingService();
    bookService.messageHandler(
        "{\"event_type\":\"book\",\"asset_id\":\""
            + TOKEN_ID
            + "\",\"market\":\""
            + CONDITION_ID
            + "\",\"timestamp\":\"1669149841000\",\"hash\":\"0xa\","
            + "\"bids\":[],\"asks\":[]}");
    assertEquals(List.of("market_" + TOKEN_ID), bookService.channels);
    assertEquals("book", bookService.messages.get(0).path("event_type").asText());
  }

  @Test
  void heartbeatReplyIsSwallowedBeforeJsonParsing() throws Exception {
    CapturingService service = new CapturingService(null, null, null);
    service.messageHandler("PONG");
    assertEquals(0, service.parsedMessages, "PONG must never reach the JSON parser");

    service.messageHandler("{\"event_type\":\"book\",\"asset_id\":\"" + TOKEN_ID + "\"}");
    assertEquals(1, service.parsedMessages);
  }

  @Test
  void scheduledPingFiresWhileConnectedRegardlessOfInboundTraffic() {
    TestScheduler scheduler = new TestScheduler();
    CapturingService service =
        new CapturingService(WS_URL, null, null, null, scheduler, 10);
    service.startHeartbeat();

    // Continuous inbound traffic never makes the stream reader-idle; the ping is a writer task.
    for (int i = 0; i < 100; i++) {
      service.messageHandler("{\"event_type\":\"book\",\"asset_id\":\"" + TOKEN_ID + "\"}");
    }
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
    assertEquals(1, pingCount(service.sent), "the first ping fires exactly on the 10s tick");
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
    assertEquals(2, pingCount(service.sent), "the ping keeps firing every 10s");
    service.stopHeartbeat();
  }

  @Test
  void disconnectCancelsThePingTask() {
    TestScheduler scheduler = new TestScheduler();
    CapturingService service =
        new CapturingService(WS_URL, null, null, null, scheduler, 10);
    service.startHeartbeat();
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
    assertEquals(1, pingCount(service.sent));

    service.disconnect().blockingAwait();
    scheduler.advanceTimeBy(60, TimeUnit.SECONDS);
    assertEquals(1, pingCount(service.sent), "no pings after the connection is torn down");
  }

  @Test
  void reconnectStartsExactlyOnePingTask() {
    TestScheduler scheduler = new TestScheduler();
    CapturingService service =
        new CapturingService(WS_URL, null, null, null, scheduler, 10);
    // Simulate two connection lifecycles; the second must replace, not stack, the first task.
    service.startHeartbeat();
    service.startHeartbeat();
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
    assertEquals(1, pingCount(service.sent), "exactly one task means one ping per interval");
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
    assertEquals(2, pingCount(service.sent));
    service.stopHeartbeat();
  }

  @Test
  void reconnectResubscriptionRestartsWithTheInitialFrame() throws Exception {
    CapturingService service = new CapturingService(null, null, null);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID);
    assertEquals(1, service.sent.size());
    assertEquals("market", MAPPER.readTree(service.sent.get(0)).path("type").asText());

    // Reconnect path: every live subscription is re-sent, and the first frame on the fresh
    // connection is the initial subscription form again.
    service.resubscribeChannels();

    assertEquals(2, service.sent.size());
    JsonNode resent = MAPPER.readTree(service.sent.get(1));
    assertEquals("market", resent.path("type").asText(), "first frame after reconnect is initial");
    assertTrue(resent.path("operation").isMissingNode());
    assertEquals(TOKEN_ID, resent.path("assets_ids").get(0).asText());

    // The next subscription on the reconnected connection is a dynamic update again.
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID_B);
    JsonNode third = MAPPER.readTree(service.sent.get(2));
    assertEquals("subscribe", third.path("operation").asText());
    assertEquals(TOKEN_ID_B, third.path("assets_ids").get(0).asText());
  }

  @Test
  void reconnectResubscriptionSendsExactlyOneInitialFrame() throws Exception {
    CapturingService service = new CapturingService("key", "secret", "passphrase");
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_USER, CONDITION_ID);
    assertEquals(2, service.sent.size());

    // The channel registry is a ConcurrentHashMap, so resubscribe order is not insertion order:
    // exactly one channel re-establishes with an initial frame and every other with an update.
    service.resubscribeChannels();

    assertEquals(4, service.sent.size());
    List<JsonNode> resent = new ArrayList<>();
    resent.add(MAPPER.readTree(service.sent.get(2)));
    resent.add(MAPPER.readTree(service.sent.get(3)));
    long initialFrames = resent.stream().filter(frame -> frame.has("type")).count();
    long updateFrames =
        resent.stream()
            .filter(frame -> "subscribe".equals(frame.path("operation").asText()))
            .count();
    assertEquals(1, initialFrames, "exactly one initial frame per (re)connection");
    assertEquals(1, updateFrames, "every later frame on the connection is a dynamic update");
    assertTrue(
        resent.stream()
            .anyMatch(
                frame ->
                    "market".equals(frame.path("type").asText())
                        || "user".equals(frame.path("type").asText())));
  }

  private static long pingCount(List<String> sent) {
    return sent.stream().filter(PolymarketStreamingService.PING_TEXT::equals).count();
  }

  private static Disposable subscribeWhileDisconnected(
      PolymarketStreamingService service, String channel, String arg) {
    return service.subscribeChannel(channel, arg).subscribe(ignored -> {}, error -> {});
  }
}
