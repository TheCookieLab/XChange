package info.bitrich.xchangestream.kalshi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.reactivex.rxjava3.disposables.Disposable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.kalshi.client.KalshiDigest;

/**
 * Deterministic protocol tests for {@link KalshiStreamingService}: subscribe/unsubscribe frames,
 * RSA-PSS handshake headers, acknowledgement-driven sid routing, error propagation, and
 * reconnect-style resubscription — all without a live WebSocket.
 */
class KalshiStreamingServiceTest {

  private static final String WS_URL = "wss://stream.test/trade-api/ws/v2";
  private static final String TICKER = "KXSB-26";
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  private static KeyPair keyPair;
  private static KalshiDigest digest;

  /** Records outbound frames instead of writing them to a socket. */
  private static final class CapturingService extends KalshiStreamingService {
    private final List<String> sent = new ArrayList<>();

    CapturingService(String apiKey, KalshiDigest digest, Supplier<Long> clock) {
      super(WS_URL, apiKey, digest, clock);
    }

    @Override
    public void sendMessage(String message) {
      if (message != null) {
        sent.add(message);
      }
    }
  }

  @BeforeAll
  static void generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keyPair = generator.generateKeyPair();
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    digest = KalshiDigest.createInstance(pem);
  }

  @Test
  void orderBookSubscribeFramePinsUseYesPriceExactly() throws Exception {
    CapturingService service = new CapturingService("test-key-id", digest, () -> 0L);
    subscribeOnOpenChannel(service, KalshiStreamingService.CHANNEL_ORDERBOOK, TICKER);

    assertEquals(1, service.sent.size());
    JsonNode frame = MAPPER.readTree(service.sent.get(0));
    assertEquals(
        MAPPER.readTree(
            "{\"id\":1,\"cmd\":\"subscribe\",\"params\":{"
                + "\"channels\":[\"orderbook_delta\"],"
                + "\"market_tickers\":[\"KXSB-26\"],\"use_yes_price\":true}}"),
        frame,
        "order-book subscriptions must pin the unified yes-leg price scale explicitly");
  }

  @Test
  void nonOrderBookSubscribeFramesOmitUseYesPrice() throws Exception {
    CapturingService service = new CapturingService("test-key-id", digest, () -> 0L);
    subscribeOnOpenChannel(service, KalshiStreamingService.CHANNEL_TRADE, TICKER);

    assertEquals(1, service.sent.size());
    JsonNode frame = MAPPER.readTree(service.sent.get(0));
    assertEquals(
        MAPPER.readTree(
            "{\"id\":1,\"cmd\":\"subscribe\",\"params\":{"
                + "\"channels\":[\"trade\"],\"market_tickers\":[\"KXSB-26\"]}}"),
        frame,
        "non order-book channels must not carry the order-book pricing parameter");
    assertFalse(frame.path("params").has("use_yes_price"));
  }

  @Test
  void handshakeHeadersReuseTheRestSigningRule() throws Exception {
    CapturingService service =
        new CapturingService("test-key-id", digest, () -> 1754230000000L);

    DefaultHttpHeaders headers = service.getCustomHeaders();
    assertEquals("test-key-id", headers.get("KALSHI-ACCESS-KEY"));
    assertEquals("1754230000000", headers.get("KALSHI-ACCESS-TIMESTAMP"));
    assertEquals(
        Set.of("KALSHI-ACCESS-KEY", "KALSHI-ACCESS-TIMESTAMP", "KALSHI-ACCESS-SIGNATURE"),
        headers.names(),
        "the authenticated handshake must always emit exactly the three Kalshi headers");

    Signature verifier = Signature.getInstance("RSASSA-PSS");
    verifier.setParameter(
        new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
    verifier.initVerify(keyPair.getPublic());
    verifier.update("1754230000000GET/trade-api/ws/v2".getBytes(StandardCharsets.UTF_8));
    assertTrue(
        verifier.verify(
            Base64.getDecoder().decode(headers.get("KALSHI-ACCESS-SIGNATURE"))),
        "handshake signature must verify over timestamp + GET + ws path");
  }

  @Test
  void constructorRequiresBothCredentialHalves() {
    ExchangeSecurityException missingKey =
        assertThrows(
            ExchangeSecurityException.class,
            () -> new CapturingService(null, digest, () -> 0L));
    assertTrue(missingKey.getMessage().contains("apiKey"));

    ExchangeSecurityException missingDigest =
        assertThrows(
            ExchangeSecurityException.class,
            () -> new CapturingService("test-key-id", null, () -> 0L));
    assertTrue(missingDigest.getMessage().contains("secretKey"));
  }

  @Test
  void acknowledgementBindsSidToTheSubscription() throws Exception {
    CapturingService service = new CapturingService("test-key-id", digest, () -> 0L);
    subscribeOnOpenChannel(service, KalshiStreamingService.CHANNEL_ORDERBOOK, TICKER);
    service.messageHandler(
        "{\"id\":1,\"type\":\"subscribed\",\"msg\":{\"channel\":\"orderbook_delta\",\"sid\":7}}");

    String routed =
        service.getChannelNameFromMessage(
            MAPPER.readTree(
                "{\"type\":\"orderbook_delta\",\"sid\":7,\"seq\":3,\"msg\":{}}"));
    assertEquals("orderbook_delta_" + TICKER, routed);
    assertNull(
        service.getChannelNameFromMessage(
            MAPPER.readTree("{\"type\":\"trade\",\"sid\":8,\"msg\":{}}")),
        "unknown sids must not route anywhere");
  }

  @Test
  void subscriptionErrorConsumesThePendingRequest() throws Exception {
    CapturingService service = new CapturingService("test-key-id", digest, () -> 0L);
    subscribeOnOpenChannel(service, KalshiStreamingService.CHANNEL_FILL, TICKER);
    service.messageHandler(
        "{\"id\":1,\"type\":\"error\",\"msg\":{\"code\":12,\"msg\":\"not authorized\"}}");

    // A late ack for the failed request must not resurrect any sid mapping.
    service.messageHandler(
        "{\"id\":1,\"type\":\"subscribed\",\"msg\":{\"channel\":\"fill\",\"sid\":9}}");
    assertNull(
        service.getChannelNameFromMessage(
            MAPPER.readTree("{\"type\":\"fill\",\"sid\":9,\"msg\":{}}")));
  }

  @Test
  void unsubscribeFrameCarriesTheServerSidOnlyAfterAcknowledgement() throws Exception {
    CapturingService service = new CapturingService("test-key-id", digest, () -> 0L);
    subscribeOnOpenChannel(service, KalshiStreamingService.CHANNEL_TRADE, TICKER);

    assertNull(
        service.getUnsubscribeMessage("trade_" + TICKER, TICKER),
        "no sid is known before the acknowledgement, so nothing may be sent");

    service.messageHandler(
        "{\"id\":1,\"type\":\"subscribed\",\"msg\":{\"channel\":\"trade\",\"sid\":11}}");

    JsonNode frame =
        MAPPER.readTree(service.getUnsubscribeMessage("trade_" + TICKER, TICKER));
    assertEquals("unsubscribe", frame.path("cmd").asText());
    assertEquals(11, frame.path("params").path("sids").get(0).asInt());
  }

  @Test
  void resubscribeChannelsResendsFreshSubscribeFrames() throws Exception {
    CapturingService service = new CapturingService("test-key-id", digest, () -> 0L);
    subscribeOnOpenChannel(service, KalshiStreamingService.CHANNEL_ORDERBOOK, TICKER);
    subscribeOnOpenChannel(service, KalshiStreamingService.CHANNEL_TICKER, TICKER);
    service.messageHandler(
        "{\"id\":1,\"type\":\"subscribed\",\"msg\":{\"channel\":\"orderbook_delta\",\"sid\":7}}");
    service.messageHandler(
        "{\"id\":2,\"type\":\"subscribed\",\"msg\":{\"channel\":\"ticker\",\"sid\":8}}");
    assertEquals(2, service.sent.size());

    // Reconnect path: the dead connection's sids are gone, every live subscription is re-sent.
    // The channel registry is a ConcurrentHashMap, so resubscribe order is not insertion order.
    service.resubscribeChannels();

    assertEquals(4, service.sent.size());
    Map<String, JsonNode> resubscribedByChannel = new ConcurrentHashMap<>();
    for (int i = 2; i < 4; i++) {
      JsonNode frame = MAPPER.readTree(service.sent.get(i));
      resubscribedByChannel.put(frame.path("params").path("channels").get(0).asText(), frame);
    }
    JsonNode bookFrame = resubscribedByChannel.get("orderbook_delta");
    JsonNode tickerFrame = resubscribedByChannel.get("ticker");
    assertEquals(
        Set.of(3, 4),
        Set.of(bookFrame.path("id").asInt(), tickerFrame.path("id").asInt()),
        "each live subscription gets a fresh request id");
    assertEquals(TICKER, bookFrame.path("params").path("market_tickers").get(0).asText());
    assertEquals(TICKER, tickerFrame.path("params").path("market_tickers").get(0).asText());
    assertTrue(
        bookFrame.path("params").path("use_yes_price").asBoolean(),
        "resubscribed order-book frames must keep the pinned yes-leg pricing mode");
    assertFalse(tickerFrame.path("params").has("use_yes_price"));

    // Fresh acknowledgements restore routing under the new sids.
    service.messageHandler(
        "{\"id\":"
            + bookFrame.path("id").asInt()
            + ",\"type\":\"subscribed\",\"msg\":{\"channel\":\"orderbook_delta\",\"sid\":17}}");
    assertEquals(
        "orderbook_delta_" + TICKER,
        service.getChannelNameFromMessage(
            MAPPER.readTree("{\"type\":\"orderbook_delta\",\"sid\":17,\"seq\":1,\"msg\":{}}")));
  }

  /**
   * Subscribes on a fake open channel. The base {@code subscribeChannel} terminal-errors a closed
   * socket without registering or emitting a frame, so protocol tests open the channel through
   * {@link #forceOpenChannel} and only the subscriber then sees the real frame stream.
   */
  private static Disposable subscribeOnOpenChannel(
      KalshiStreamingService service, String channel, String ticker) throws Exception {
    forceOpenChannel(service);
    return service.subscribeChannel(channel, ticker).subscribe(ignored -> {}, error -> {});
  }

  /**
   * Pokes an always-open channel into the base class: {@code webSocketChannel} is private with no
   * setter, so the test replaces it via reflection (the same technique as the MEXC streaming
   * tests).
   */
  private static void forceOpenChannel(KalshiStreamingService service) throws Exception {
    Field channelField = NettyStreamingService.class.getDeclaredField("webSocketChannel");
    channelField.setAccessible(true);
    channelField.set(service, new EmbeddedChannel());
  }
}
