package info.bitrich.xchangestream.kucoin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UTA-generation WebSocket service.
 *
 * <p>Implements the current UTA protocol: {@code {T, P, d}} frames, {@code SUBSCRIBE}/{@code
 * UNSUBSCRIBE} actions with acks, the {@code welcome} message carrying {@code pingInterval}, JSON
 * ping/pong frames (never more than one per second), per-connection generation ids, and token
 * re-acquisition on private reconnects. Subscription payloads are queued until the server welcome
 * arrives so private sockets never race the handshake.
 */
public class UtaStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(UtaStreamingService.class);

  /** Subscription id separator; avoids symbol collisions (symbols contain '-'). */
  static final String ID_SEPARATOR = "|";

  private final AtomicLong refCount = new AtomicLong();
  private final AtomicLong generation = new AtomicLong();
  private final boolean privateChannel;
  private final String baseEndpoint;
  private final Supplier<String> tokenSupplier;

  private volatile boolean welcomed;
  private final Queue<String> pendingSubscriptions = new ArrayDeque<>();
  private Disposable pingSubscription;

  /**
   * @param apiUrl full connection URL for the first connection
   * @param privateChannel true when this socket requires a private token
   * @param tokenSupplier supplies a fresh private token on every (re)connect; {@code null} for
   *     public sockets
   */
  public UtaStreamingService(String apiUrl, boolean privateChannel, Supplier<String> tokenSupplier) {
    super(apiUrl);
    this.privateChannel = privateChannel;
    this.baseEndpoint = apiUrl;
    this.tokenSupplier = privateChannel ? tokenSupplier : null;
  }

  /** @return the generation id of the current physical connection; increments per (re)connect */
  public long getGeneration() {
    return generation.get();
  }

  public boolean isPrivateChannel() {
    return privateChannel;
  }

  @Override
  protected Completable openConnection() {
    if (tokenSupplier != null) {
      try {
        String token = tokenSupplier.get();
        this.uri =
            URI.create(
                baseEndpoint + (baseEndpoint.contains("?") ? "&" : "?") + "token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8));
      } catch (RuntimeException e) {
        return Completable.error(
            new IOException("Failed to acquire UTA private WebSocket token", e));
      }
    }
    generation.incrementAndGet();
    return super.openConnection();
  }

  @Override
  protected void handleMessage(JsonNode message) {
    String type = message.path("type").asText(null);
    if ("pong".equals(type)) {
      // Pong answers our app-level ping; socket liveness is handled by Netty.
      return;
    }
    if ("welcome".equals(message.path("message").asText(null))) {
      onWelcome(message);
      return;
    }
    if ("error".equals(type)) {
      super.handleError(message, new Exception(message.path("data").asText("UTA websocket error")));
      return;
    }
    if (message.has("id") && message.has("result")) {
      LOG.debug("UTA subscription ack: {}", message);
      return;
    }
    super.handleMessage(message);
  }

  private void onWelcome(JsonNode welcome) {
    welcomed = true;
    int pingInterval = welcome.path("pingInterval").asInt(30000);
    if (pingSubscription != null && !pingSubscription.isDisposed()) {
      pingSubscription.dispose();
    }
    pingSubscription =
        Observable.interval(pingInterval, pingInterval, TimeUnit.MILLISECONDS)
            .subscribe(
                tick -> {
                  if (isSocketOpen()) {
                    sendMessage(
                        "{\"id\":" + refCount.incrementAndGet() + ",\"type\":\"ping\"}");
                  }
                },
                e -> LOG.warn("UTA ping loop failed", e));
    flushPendingSubscriptions();
  }

  private void flushPendingSubscriptions() {
    while (!pendingSubscriptions.isEmpty()) {
      sendMessage(pendingSubscriptions.poll());
    }
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    String topic = message.path("T").asText(null);
    if (topic == null) {
      return null;
    }
    int dot = topic.indexOf('.');
    if (dot < 0) {
      return null;
    }
    String channel = topic.substring(0, dot);
    String tradeType = topic.substring(dot + 1);
    if ("balance".equals(channel) || "orderAll".equals(channel) || "positionAll".equals(channel)) {
      return channel + ID_SEPARATOR + tradeType;
    }
    String symbol = message.path("d").path("s").asText(null);
    String depth = message.path("dp").asText(null);
    StringBuilder id = new StringBuilder(channel).append(ID_SEPARATOR).append(tradeType);
    if (symbol != null) {
      id.append(ID_SEPARATOR).append(symbol);
    }
    if (depth != null && !depth.isEmpty()) {
      id.append(ID_SEPARATOR).append(depth);
    }
    return id.toString();
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    StringBuilder id = new StringBuilder(channelName);
    if (args != null) {
      for (Object arg : args) {
        if (arg != null && !arg.toString().isEmpty()) {
          id.append(ID_SEPARATOR).append(arg);
        }
      }
    }
    return id.toString();
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    return subscribeMessage("SUBSCRIBE", channelName, args);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    return subscribeMessage("UNSUBSCRIBE", channelName, args);
  }

  private String subscribeMessage(String action, String channelName, Object... args)
      throws IOException {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("id", refCount.incrementAndGet());
    node.put("action", action);
    node.put("channel", channelName);
    if (args != null) {
      if (args.length > 0 && args[0] != null) {
        node.put("tradeType", args[0].toString());
      }
      if (args.length > 1 && args[1] != null) {
        node.put("symbol", args[1].toString());
      }
      if (args.length > 2 && args[2] != null) {
        node.put("depth", args[2].toString());
      }
      if (args.length > 3 && args[3] != null) {
        node.put("interval", args[3].toString());
      }
      if (args.length > 4 && args[4] != null) {
        node.put("accountType", args[4].toString());
      }
    }
    String message = objectMapper.writeValueAsString(node);
    if (!welcomed) {
      pendingSubscriptions.add(message);
    }
    return message;
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshaker, WebSocketClientHandler.WebSocketMessageHandler handler) {
    return new UtaNettyWebSocketClientHandler(handshaker, handler);
  }

  private class UtaNettyWebSocketClientHandler extends NettyWebSocketClientHandler {
    public UtaNettyWebSocketClientHandler(
        WebSocketClientHandshaker handshaker, WebSocketMessageHandler handler) {
      super(handshaker, handler);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      welcomed = false;
      if (pingSubscription != null && !pingSubscription.isDisposed()) {
        pingSubscription.dispose();
      }
      super.channelInactive(ctx);
    }
  }
}
