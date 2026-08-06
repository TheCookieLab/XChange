package info.bitrich.xchangestream.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/**
 * WebSocket protocol handler for the Polymarket CLOB streams. One instance serves the public
 * market channel ({@code /ws/market}); a second instance with L2 credentials serves the user
 * channel ({@code /ws/user}).
 *
 * <p>Protocol notes (docs.polymarket.com CLOB websocket reference):
 *
 * <ul>
 *   <li>Only the <em>first</em> subscription on a connection may use the initial frame: market
 *       {@code {"assets_ids":[token...],"type":"market"}}, user {@code {"auth":{apiKey,secret,
 *       passphrase},"markets":[conditionId...],"type":"user"}}. Every later subscription on the
 *       same connection must be a dynamic update frame {@code {"operation":"subscribe",...}}; the
 *       base class re-sends every channel's subscribe message after a reconnect, so the
 *       initial-versus-dynamic state is reset when the channels are re-subscribed.
 *   <li>Dynamic unsubscribe sends the same id list with {@code "operation":"unsubscribe"}.
 *   <li>The heartbeat is application-level: the <em>client</em> must send a text {@code PING}
 *       every ten seconds regardless of inbound traffic, and the server replies {@code PONG}
 *       (swallowed before JSON parsing). A scheduled writer task tied to the connection lifecycle
 *       emits the ping; it starts on connect, is cancelled on disconnect or channel close, and a
 *       reconnect starts exactly one new task.
 *   <li>Events carry no subscription id, so routing derives the unique channel id from the
 *       payload: {@code market_<assetId>} for market events and {@code user_<conditionId>} for
 *       user events, matching {@link #getSubscriptionUniqueId} on the subscribe side. A
 *       {@code price_change} event can batch level updates for several outcome tokens; the
 *       dispatch path splits it into one single-asset node per change so each {@code
 *       market_<assetId>} channel only ever sees its own updates.
 * </ul>
 */
public class PolymarketStreamingService extends JsonNettyStreamingService {

  /** Public market channel name (books, price changes, last trades). */
  public static final String CHANNEL_MARKET = "market";

  /** Authenticated user channel name (order and trade updates). */
  public static final String CHANNEL_USER = "user";

  /** Heartbeat text the server expects every ten seconds. */
  static final String PING_TEXT = "PING";

  /** Heartbeat reply text; swallowed before JSON parsing. */
  static final String PONG_TEXT = "PONG";

  private static final long HEARTBEAT_INTERVAL_SECONDS = 10;
  private static final int MAX_FRAME_PAYLOAD_LENGTH = 65536;

  private final String apiKey;
  private final String secret;
  private final String passphrase;
  private final Scheduler heartbeatScheduler;
  private final long heartbeatIntervalSeconds;
  private final Object heartbeatLock = new Object();
  private Disposable heartbeatTask;
  private volatile boolean initialFrameSent;

  /**
   * @param apiUrl full WebSocket URI of one CLOB channel
   * @param apiKey L2 API key ({@code null} on the public market channel)
   * @param secret L2 API secret ({@code null} on the public market channel)
   * @param passphrase L2 API passphrase ({@code null} on the public market channel)
   */
  public PolymarketStreamingService(
      String apiUrl, String apiKey, String secret, String passphrase) {
    this(apiUrl, apiKey, secret, passphrase, Schedulers.computation(), HEARTBEAT_INTERVAL_SECONDS);
  }

  /**
   * Test seam: binds the writer heartbeat to an injectable scheduler and interval. The reader-idle
   * handler is disabled entirely because Polymarket's heartbeat is a client-driven text ping, not
   * an idle-triggered protocol frame.
   */
  PolymarketStreamingService(
      String apiUrl,
      String apiKey,
      String secret,
      String passphrase,
      Scheduler heartbeatScheduler,
      long heartbeatIntervalSeconds) {
    super(
        apiUrl,
        MAX_FRAME_PAYLOAD_LENGTH,
        DEFAULT_CONNECTION_TIMEOUT,
        DEFAULT_RETRY_DURATION,
        0);
    this.apiKey = apiKey;
    this.secret = secret;
    this.passphrase = passphrase;
    this.heartbeatScheduler = heartbeatScheduler;
    this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
  }

  /** @return true when the full L2 credential triplet is present for user-channel auth */
  public boolean hasCredentials() {
    return apiKey != null
        && !apiKey.isBlank()
        && secret != null
        && !secret.isBlank()
        && passphrase != null
        && !passphrase.isBlank();
  }

  /**
   * Builds the subscribe frame. The first subscription on a connection uses the initial frame
   * ({@code type} = {@code market} or {@code user}, with {@code auth} on the user channel); every
   * later subscription on the same connection uses a dynamic update frame ({@code operation} =
   * {@code subscribe}). {@link #resubscribeChannels} resets the state after a (re)connect so the
   * first frame on the fresh connection is again the initial form.
   */
  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    ObjectNode frame = objectMapper.createObjectNode();
    boolean user = CHANNEL_USER.equals(channelName);
    if (user && !hasCredentials()) {
      throw new ExchangeSecurityException(
          "Polymarket user channel requires the apiKey, secretKey, and password (L2 passphrase)"
              + " credentials");
    }
    if (initialFrameSent) {
      frame.put("operation", "subscribe");
    } else {
      initialFrameSent = true;
      if (user) {
        ObjectNode auth = frame.putObject("auth");
        auth.put("apiKey", apiKey);
        auth.put("secret", secret);
        auth.put("passphrase", passphrase);
      }
      frame.put("type", user ? CHANNEL_USER : CHANNEL_MARKET);
    }
    putStrings(frame.putArray(user ? "markets" : "assets_ids"), args);
    return objectMapper.writeValueAsString(frame);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    ObjectNode frame = objectMapper.createObjectNode();
    if (CHANNEL_USER.equals(channelName)) {
      putStrings(frame.putArray("markets"), args);
    } else {
      putStrings(frame.putArray("assets_ids"), args);
    }
    frame.put("operation", "unsubscribe");
    return objectMapper.writeValueAsString(frame);
  }

  @Override
  public String getChannelNameFromMessage(JsonNode message) {
    return switch (message.path("event_type").asText("")) {
      case "book", "last_trade_price", "tick_size_change", "best_bid_ask" ->
          marketChannelId(message.path("asset_id").asText(null));
      case "price_change" -> {
        JsonNode changes = message.path("price_changes");
        yield changes.isArray() && !changes.isEmpty()
            ? marketChannelId(changes.get(0).path("asset_id").asText(null))
            : null;
      }
      case "order", "trade" -> userChannelId(message.path("market").asText(null));
      default -> null;
    };
  }

  /**
   * Dispatch override for batched {@code price_change} events: the wire schema allows one event to
   * carry level updates for several outcome tokens, but each {@code market_<assetId>} channel may
   * only ever see its own updates. The event is split into one synthetic single-change node per
   * {@code price_changes} element, each routed to the channel of its own {@code asset_id};
   * everything else keeps the single-channel routing of {@link #getChannelNameFromMessage}.
   */
  @Override
  protected void handleMessage(JsonNode message) {
    if (message instanceof ObjectNode && isBatchedPriceChange(message)) {
      for (JsonNode change : message.path("price_changes")) {
        String channel = marketChannelId(change.path("asset_id").asText(null));
        if (channel == null) {
          continue;
        }
        ObjectNode single = ((ObjectNode) message).deepCopy();
        single.putArray("price_changes").add(change);
        handleChannelMessage(channel, single);
      }
      return;
    }
    super.handleMessage(message);
  }

  /** Starts the connection-bound writer heartbeat, replacing any previous task. */
  void startHeartbeat() {
    synchronized (heartbeatLock) {
      stopHeartbeatLocked();
      heartbeatTask =
          heartbeatScheduler.schedulePeriodicallyDirect(
              this::sendHeartbeatPing,
              heartbeatIntervalSeconds,
              heartbeatIntervalSeconds,
              TimeUnit.SECONDS);
    }
  }

  /** Cancels the writer heartbeat; idempotent. */
  void stopHeartbeat() {
    synchronized (heartbeatLock) {
      stopHeartbeatLocked();
    }
  }

  /**
   * Sends the application-level text ping. {@link #sendMessage} no-ops when the socket is closed,
   * which makes a stale task harmless in the reconnect window, but the task is still cancelled on
   * close so a reconnect starts exactly one fresh task.
   */
  private void sendHeartbeatPing() {
    sendMessage(PING_TEXT);
  }

  private void stopHeartbeatLocked() {
    if (heartbeatTask != null) {
      // dispose() is idempotent; the reference is kept so a later startHeartbeat can replace it.
      heartbeatTask.dispose();
    }
  }

  @Override
  protected Completable openConnection() {
    return super.openConnection().doOnComplete(this::startHeartbeat);
  }

  @Override
  public Completable disconnect() {
    stopHeartbeat();
    return super.disconnect();
  }

  /** Cancels the writer heartbeat whenever the socket closes, including unexpected closes. */
  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshaker,
      WebSocketClientHandler.WebSocketMessageHandler messageHandler) {
    return new NettyWebSocketClientHandler(handshaker, messageHandler) {
      @Override
      public void channelInactive(ChannelHandlerContext ctx) {
        stopHeartbeat();
        super.channelInactive(ctx);
      }
    };
  }

  /**
   * Every (re)connection re-subscribes the live channels, and the first frame on the fresh
   * connection must again be the initial subscription form, so the initial-versus-dynamic state is
   * reset here before the base re-sends the subscribe messages.
   */
  @Override
  public void resubscribeChannels() {
    initialFrameSent = false;
    super.resubscribeChannels();
  }

  /** Swallows heartbeat replies that are not JSON before the base parser logs an error. */
  @Override
  public void messageHandler(String message) {
    if (PONG_TEXT.equals(message)) {
      return;
    }
    super.messageHandler(message);
  }

  private static boolean isBatchedPriceChange(JsonNode message) {
    JsonNode changes = message.path("price_changes");
    return "price_change".equals(message.path("event_type").asText(""))
        && changes.isArray()
        && changes.size() > 1;
  }

  private static String marketChannelId(String assetId) {
    return assetId == null ? null : CHANNEL_MARKET + "_" + assetId;
  }

  private static String userChannelId(String conditionId) {
    return conditionId == null ? null : CHANNEL_USER + "_" + conditionId;
  }

  private static void putStrings(ArrayNode array, Object... args) {
    for (Object arg : args) {
      array.add(String.valueOf(arg));
    }
  }
}
