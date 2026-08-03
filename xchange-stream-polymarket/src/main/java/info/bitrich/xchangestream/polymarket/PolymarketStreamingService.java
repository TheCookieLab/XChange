package info.bitrich.xchangestream.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.io.IOException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/**
 * WebSocket protocol handler for the Polymarket CLOB streams. One instance serves the public
 * market channel ({@code /ws/market}); a second instance with L2 credentials serves the user
 * channel ({@code /ws/user}).
 *
 * <p>Protocol notes (docs.polymarket.com CLOB websocket reference):
 *
 * <ul>
 *   <li>Market subscriptions send {@code {"assets_ids":[token...],"type":"market"}}; user
 *       subscriptions send {@code {"auth":{apiKey,secret,passphrase},"markets":[conditionId...],
 *       "type":"user"}}. There is no subscription acknowledgement: events simply start flowing.
 *   <li>Dynamic unsubscribe sends the same id list with {@code "operation":"unsubscribe"}.
 *   <li>The heartbeat is application-level: a text {@code PING} frame, answered by {@code PONG}.
 *       The base idle handler fires on reader idle, so on an active stream (constant inbound
 *       traffic) no ping is needed; on a quiet stream a ping goes out every idle interval and the
 *       inbound {@code PONG} re-arms the timer, keeping the documented cadence.
 *   <li>Events carry no subscription id, so routing derives the unique channel id from the
 *       payload: {@code market_<assetId>} for market events and {@code user_<conditionId>} for
 *       user events, matching {@link #getSubscriptionUniqueId} on the subscribe side.
 * </ul>
 */
public class PolymarketStreamingService extends JsonNettyStreamingService {

  /** Public market channel name (books, price changes, last trades). */
  public static final String CHANNEL_MARKET = "market";

  /** Authenticated user channel name (order and trade updates). */
  public static final String CHANNEL_USER = "user";

  /** Heartbeat text the server expects roughly every ten seconds. */
  static final String PING_TEXT = "PING";

  /** Heartbeat reply text; swallowed before JSON parsing. */
  static final String PONG_TEXT = "PONG";

  private static final int HEARTBEAT_IDLE_SECONDS = 10;
  private static final int MAX_FRAME_PAYLOAD_LENGTH = 65536;

  private final String apiKey;
  private final String secret;
  private final String passphrase;

  /**
   * @param apiUrl full WebSocket URI of one CLOB channel
   * @param apiKey L2 API key ({@code null} on the public market channel)
   * @param secret L2 API secret ({@code null} on the public market channel)
   * @param passphrase L2 API passphrase ({@code null} on the public market channel)
   */
  public PolymarketStreamingService(
      String apiUrl, String apiKey, String secret, String passphrase) {
    super(
        apiUrl,
        MAX_FRAME_PAYLOAD_LENGTH,
        DEFAULT_CONNECTION_TIMEOUT,
        DEFAULT_RETRY_DURATION,
        HEARTBEAT_IDLE_SECONDS);
    this.apiKey = apiKey;
    this.secret = secret;
    this.passphrase = passphrase;
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

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    ObjectNode frame = objectMapper.createObjectNode();
    if (CHANNEL_USER.equals(channelName)) {
      if (!hasCredentials()) {
        throw new ExchangeSecurityException(
            "Polymarket user channel requires the apiKey, secretKey, and password (L2 passphrase)"
                + " credentials");
      }
      ObjectNode auth = frame.putObject("auth");
      auth.put("apiKey", apiKey);
      auth.put("secret", secret);
      auth.put("passphrase", passphrase);
      putStrings(frame.putArray("markets"), args);
      frame.put("type", CHANNEL_USER);
    } else {
      putStrings(frame.putArray("assets_ids"), args);
      frame.put("type", CHANNEL_MARKET);
    }
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

  /** Sends the application-level text ping instead of a protocol-level ping frame. */
  @Override
  protected void handleIdle(ChannelHandlerContext ctx) {
    ctx.writeAndFlush(new TextWebSocketFrame(PING_TEXT));
  }

  /** Swallows heartbeat replies that are not JSON before the base parser logs an error. */
  @Override
  public void messageHandler(String message) {
    if (PONG_TEXT.equals(message)) {
      return;
    }
    super.messageHandler(message);
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
