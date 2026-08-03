package info.bitrich.xchangestream.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/**
 * Deterministic protocol tests for {@link PolymarketStreamingService}: subscribe/unsubscribe frame
 * shapes, credential gating, payload-derived routing, heartbeat handling, and reconnect-style
 * resubscription — all without a live WebSocket.
 */
class PolymarketStreamingServiceTest {

  private static final String WS_URL = "wss://stream.test/ws/market";
  private static final String CONDITION_ID =
      "0x9b0f6b43e1a44c2fb2d3a1e5c7d8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6";
  private static final String TOKEN_ID =
      "104173557214744537570424345347209544585775842950109756851652855913015295508992";
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  /** Records outbound frames and counts JSON dispatches instead of writing to a socket. */
  private static final class CapturingService extends PolymarketStreamingService {
    private final List<String> sent = new ArrayList<>();
    private int parsedMessages;

    CapturingService(String apiKey, String secret, String passphrase) {
      super(WS_URL, apiKey, secret, passphrase);
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

  @Test
  void marketSubscribeFrameCarriesTheTokenIds() throws Exception {
    CapturingService service = new CapturingService(null, null, null);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID);

    assertEquals(1, service.sent.size());
    JsonNode frame = MAPPER.readTree(service.sent.get(0));
    assertEquals("market", frame.path("type").asText());
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
  void heartbeatReplyIsSwallowedBeforeJsonParsing() throws Exception {
    CapturingService service = new CapturingService(null, null, null);
    service.messageHandler("PONG");
    assertEquals(0, service.parsedMessages, "PONG must never reach the JSON parser");

    service.messageHandler("{\"event_type\":\"book\",\"asset_id\":\"" + TOKEN_ID + "\"}");
    assertEquals(1, service.parsedMessages);
  }

  @Test
  void idleHeartbeatSendsTheApplicationLevelTextPing() {
    PolymarketStreamingService service = new PolymarketStreamingService(WS_URL, null, null, null);
    EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {});
    try {
      // firstContext() is only non-null once the pipeline holds a real handler.
      service.handleIdle(channel.pipeline().firstContext());
      TextWebSocketFrame frame = channel.readOutbound();
      assertEquals("PING", frame.text());
      frame.release();
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void resubscribeChannelsResendsFreshSubscribeFrames() throws Exception {
    CapturingService service = new CapturingService("key", "secret", "passphrase");
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_MARKET, TOKEN_ID);
    subscribeWhileDisconnected(service, PolymarketStreamingService.CHANNEL_USER, CONDITION_ID);
    assertEquals(2, service.sent.size());

    // Reconnect path: every live subscription is re-sent. The channel registry is a
    // ConcurrentHashMap, so resubscribe order is not insertion order.
    service.resubscribeChannels();

    assertEquals(4, service.sent.size());
    List<JsonNode> resubscribed = new ArrayList<>();
    resubscribed.add(MAPPER.readTree(service.sent.get(2)));
    resubscribed.add(MAPPER.readTree(service.sent.get(3)));
    assertTrue(
        resubscribed.stream()
            .anyMatch(
                frame ->
                    "market".equals(frame.path("type").asText())
                        && TOKEN_ID.equals(frame.path("assets_ids").get(0).asText())),
        "resubscribe must include the market channel");
    assertTrue(
        resubscribed.stream()
            .anyMatch(
                frame ->
                    "user".equals(frame.path("type").asText())
                        && CONDITION_ID.equals(frame.path("markets").get(0).asText())),
        "resubscribe must include the user channel");
  }

  private static Disposable subscribeWhileDisconnected(
      PolymarketStreamingService service, String channel, String arg) {
    return service.subscribeChannel(channel, arg).subscribe(ignored -> {}, error -> {});
  }
}
