package info.bitrich.xchangestream.kalshi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kalshi.client.KalshiDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kalshi trade-api v2 WebSocket connection.
 *
 * <p>Protocol summary (per the official Kalshi WebSocket docs):
 *
 * <ul>
 *   <li>Handshake authentication reuses the REST RSA-PSS rule: the client sends {@code
 *       KALSHI-ACCESS-KEY}, {@code KALSHI-ACCESS-TIMESTAMP} (milliseconds) and {@code
 *       KALSHI-ACCESS-SIGNATURE} over {@code timestamp + "GET" + <ws path>}. Without credentials
 *       the handshake is anonymous and only public channels may be subscribed.
 *   <li>Subscriptions are {@code {"id": N, "cmd": "subscribe", "params": {"channels": [...],
 *       "market_tickers": [...]}}} frames; the server acknowledges with {@code type= subscribed}
 *       carrying a server-assigned {@code sid}.
 *   <li>Data messages route by {@code sid}, not by channel name, so this service resolves each
 *       inbound {@code sid} back to the XChange subscription unique id recorded when the
 *       subscription was acknowledged.
 *   <li>Unsubscribe requires the server-assigned sid: {@code {"id": N, "cmd": "unsubscribe",
 *       "params": {"sids": [sid]}}}.
 * </ul>
 *
 * <p>Reconnects re-send every live subscription via {@link #resubscribeChannels()} and rebuild the
 * sid mappings from the fresh acknowledgements; stale subscription state from the dead connection
 * is discarded in {@link #openConnection()}.
 */
public class KalshiStreamingService extends JsonNettyStreamingService {

  /** Public order-book channel: an {@code orderbook_snapshot} then sequenced {@code
   * orderbook_delta} messages. */
  public static final String CHANNEL_ORDERBOOK = "orderbook_delta";

  /** Public trades channel. */
  public static final String CHANNEL_TRADE = "trade";

  /** Public top-of-book ticker channel. */
  public static final String CHANNEL_TICKER = "ticker";

  /** Public market status transitions channel. */
  public static final String CHANNEL_MARKET_LIFECYCLE = "market_lifecycle_v2";

  /** Authenticated user fills channel. */
  public static final String CHANNEL_FILL = "fill";

  /** Authenticated user order-state channel. */
  public static final String CHANNEL_USER_ORDER = "user_orders";

  private static final Logger LOG = LoggerFactory.getLogger(KalshiStreamingService.class);

  private static final String HEADER_ACCESS_KEY = "KALSHI-ACCESS-KEY";
  private static final String HEADER_ACCESS_TIMESTAMP = "KALSHI-ACCESS-TIMESTAMP";
  private static final String HEADER_ACCESS_SIGNATURE = "KALSHI-ACCESS-SIGNATURE";

  private final String apiKey;
  private final KalshiDigest digest;
  private final Supplier<Long> timestampMillis;
  private final AtomicInteger requestId = new AtomicInteger();
  private final Map<Integer, String> pendingSubscribeById = new ConcurrentHashMap<>();
  private final Map<Integer, String> uniqueIdBySid = new ConcurrentHashMap<>();
  private final Map<String, Integer> sidByUniqueId = new ConcurrentHashMap<>();

  /**
   * @param apiUrl WebSocket URL, e.g. {@code wss://external-api-ws.kalshi.com/trade-api/ws/v2}
   * @param apiKey Kalshi API key id, or {@code null} for public-only access
   * @param digest signer built from the Kalshi RSA private key, or {@code null} for public-only
   *     access
   */
  public KalshiStreamingService(String apiUrl, String apiKey, KalshiDigest digest) {
    this(apiUrl, apiKey, digest, System::currentTimeMillis);
  }

  /** Test seam pinning the handshake timestamp. */
  KalshiStreamingService(
      String apiUrl, String apiKey, KalshiDigest digest, Supplier<Long> timestampMillis) {
    super(apiUrl);
    this.apiKey = apiKey;
    this.digest = digest;
    this.timestampMillis = timestampMillis;
  }

  /** @return {@code true} when both credential halves are present and user channels may be used */
  public boolean hasCredentials() {
    return apiKey != null && digest != null;
  }

  /**
   * Builds the RSA-PSS handshake headers. Public (widened from the protected base method) so the
   * deterministic auth-header tests can inspect them without opening a socket.
   */
  @Override
  public DefaultHttpHeaders getCustomHeaders() {
    DefaultHttpHeaders headers = new DefaultHttpHeaders();
    if (!hasCredentials()) {
      return headers;
    }
    String timestamp = String.valueOf(timestampMillis.get());
    headers
        .add(HEADER_ACCESS_KEY, apiKey)
        .add(HEADER_ACCESS_TIMESTAMP, timestamp)
        .add(HEADER_ACCESS_SIGNATURE, digest.sign(timestamp + "GET" + uri.getPath()));
    return headers;
  }

  /** Drops subscription bookkeeping from a dead connection before a fresh one is opened. */
  @Override
  protected Completable openConnection() {
    pendingSubscribeById.clear();
    uniqueIdBySid.clear();
    sidByUniqueId.clear();
    return super.openConnection();
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    int id = requestId.incrementAndGet();
    pendingSubscribeById.put(id, getSubscriptionUniqueId(channelName, args));
    ObjectNode params = objectMapper.createObjectNode();
    params.putArray("channels").add(channelName);
    if (args != null && args.length > 0) {
      ArrayNode tickers = params.putArray("market_tickers");
      for (Object arg : args) {
        tickers.add(String.valueOf(arg));
      }
    }
    ObjectNode frame = objectMapper.createObjectNode();
    frame.put("id", id);
    frame.put("cmd", "subscribe");
    frame.set("params", params);
    return objectMapper.writeValueAsString(frame);
  }

  /**
   * Builds the unsubscribe frame for a subscription unique id (the base class passes the unique
   * id, not the raw channel name). Kalshi unsubscribes by server-assigned sid; when the
   * subscription was never acknowledged there is nothing to retract server-side and {@code null}
   * is returned, which the base class skips sending.
   */
  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    Integer sid = sidByUniqueId.get(channelName);
    if (sid == null) {
      return null;
    }
    ObjectNode params = objectMapper.createObjectNode();
    params.putArray("sids").add(sid);
    ObjectNode frame = objectMapper.createObjectNode();
    frame.put("id", requestId.incrementAndGet());
    frame.put("cmd", "unsubscribe");
    frame.set("params", params);
    return objectMapper.writeValueAsString(frame);
  }

  /**
   * Resolves an inbound data message to its XChange subscription unique id via the
   * server-assigned sid. Unknown sids (for example messages racing an unsubscribe) yield {@code
   * null}, which the base class drops. Public for deterministic routing tests.
   */
  @Override
  public String getChannelNameFromMessage(JsonNode message) {
    JsonNode sid = message.get("sid");
    if (sid == null || !sid.isIntegralNumber()) {
      return null;
    }
    return uniqueIdBySid.get(sid.asInt());
  }

  /** Intercepts subscription-control frames; everything else routes to channel subscribers. */
  @Override
  protected void handleMessage(JsonNode message) {
    switch (message.path("type").asText("")) {
      case "subscribed" -> onSubscribed(message);
      case "unsubscribed" -> onUnsubscribed(message);
      case "error" -> onErrorMessage(message);
      default -> super.handleMessage(message);
    }
  }

  private void onSubscribed(JsonNode message) {
    int sid = message.path("msg").path("sid").asInt(-1);
    String uniqueId = pendingSubscribeById.remove(message.path("id").asInt(-1));
    if (uniqueId == null || sid < 0) {
      LOG.warn("Kalshi subscribe acknowledgement without a pending request: {}", message);
      return;
    }
    uniqueIdBySid.put(sid, uniqueId);
    sidByUniqueId.put(uniqueId, sid);
  }

  private void onUnsubscribed(JsonNode message) {
    String uniqueId = uniqueIdBySid.remove(message.path("msg").path("sid").asInt(-1));
    if (uniqueId != null) {
      sidByUniqueId.remove(uniqueId);
    }
  }

  private void onErrorMessage(JsonNode message) {
    JsonNode detail = message.path("msg");
    ExchangeException error =
        new ExchangeException(
            "Kalshi WebSocket error "
                + detail.path("code").asText("unknown")
                + ": "
                + detail.path("msg").asText("no detail"));
    String uniqueId = pendingSubscribeById.remove(message.path("id").asInt(-1));
    if (uniqueId != null) {
      handleChannelError(uniqueId, error);
    } else {
      LOG.error("Kalshi WebSocket error without a pending request: {}", message);
    }
  }
}
