package info.bitrich.xchangestream.mexc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket transport for MEXC Spot v3 ({@code wss://wbs-api.mexc.com/ws}).
 *
 * <p>MEXC protocol rules enforced here:
 *
 * <ul>
 *   <li>Client commands are JSON text frames ({@code SUBSCRIPTION}/{@code UNSUBSCRIPTION}/{@code
 *       PING}); the server confirms with {@code {"id":..,"code":..,"msg":"<channel>"}}.
 *   <li>Server pushes are <em>binary</em> frames whose payload is a serialized {@link
 *       PushDataV3ApiWrapper}; they are decoded to canonical JSON (see {@link MexcV3ProtoCodec})
 *       and routed to the channel named in the wrapper.
 *   <li>At most 30 subscriptions per connection; the server closes connections without a valid
 *       subscription after 30s and idle connections after 60s. The keepalive is a JSON text PING,
 *       not a WebSocket control frame.
 *   <li>The server disconnects after 24h; {@link NettyStreamingService} auto-reconnects and
 *       re-subscribes registered channels.
 * </ul>
 */
public class MexcV3StreamingService extends NettyStreamingService<String> {

  private static final Logger LOG = LoggerFactory.getLogger(MexcV3StreamingService.class);

  /** MEXC enforces at most 30 subscriptions per connection. */
  public static final int MAX_SUBSCRIPTIONS_PER_CONNECTION = 30;
  private static final String PING_MESSAGE = "{\"method\":\"PING\"}";
  private static final String PONG_MESSAGE = "{\"method\":\"PONG\"}";

  private final ObjectMapper objectMapper = StreamingObjectMapperHelper.getObjectMapper();

  /**
   * One shared observable per channel. {@link NettyStreamingService#subscribeChannel} emits one
   * event stream per channel, so every consumer of the same channel must share the same
   * subscription; otherwise a second consumer registers a second emitter that the channel map
   * never routes to. The cached observable wraps the base implementation's already-shared stream,
   * keeping subscription deduplication and reconnect re-registration semantics; the entry is
   * evicted when the last consumer disposes (so a later subscribe builds a fresh wrapper), which
   * the base's own refCount mirrors by removing the channel and unsubscribing.
   */
  private final Map<String, Observable<String>> sharedChannels = new ConcurrentHashMap<>();

  public MexcV3StreamingService(String apiUrl) {
    super(apiUrl);
  }

  public MexcV3StreamingService(String apiUrl, int maxFramePayloadLength) {
    super(apiUrl, maxFramePayloadLength);
  }

  /**
   * The user-data stream URI carries the listen key as a {@code listenKey} query parameter; it
   * must reach the server for private-channel authorization but must never appear in logs.
   */
  @Override
  protected URI getLogSafeUri() {
    String redacted = uri.toString().replaceAll("([?&]listenKey=)[^&]*", "$1REDACTED");
    if (redacted.equals(uri.toString())) {
      return uri;
    }
    try {
      return new URI(redacted);
    } catch (URISyntaxException e) {
      return uri;
    }
  }

  /** Handles text frames: subscription acks, PING/PONG keepalive, and (defensively) text pushes. */
  @Override
  public void messageHandler(String message) {
    JsonNode node;
    try {
      node = objectMapper.readTree(message);
    } catch (IOException e) {
      LOG.error("MEXC v3 text frame is not valid JSON: {}", message);
      return;
    }
    if (node.has("channel")) {
      // Text push envelope (MEXC normally pushes binary frames, but route it anyway).
      handleMessage(message);
      return;
    }
    if ("PING".equals(node.path("method").asText())) {
      sendMessage(PONG_MESSAGE);
      return;
    }
    // Subscription confirmation: {"id":..,"code":0,"msg":"<channel>"} on success, or a PONG
    // ack. MEXC documents code 0 as command success; HTTP-style 200 is accepted too. Anything
    // else is a rejection.
    JsonNode ackCode = node.get("code");
    int code = ackCode == null ? 0 : ackCode.asInt();
    if (code != 0 && code != 200) {
      // Non-success command acknowledgement: the server rejected the subscription (invalid
      // channel, server-side subscription-limit rejection, ...). Fail the affected channel so
      // its subscribers get an error signal instead of waiting forever for events that cannot
      // arrive, and drop the channel so a later subscribe retries from scratch.
      String channel = node.path("msg").asText("");
      LOG.warn("MEXC v3 rejected subscription {}: code {}", channel, code);
      Subscription subscription = channels.remove(channel);
      if (subscription != null) {
        sharedChannels.remove(channel);
        subscription
            .getEmitter()
            .onError(
                new ExchangeException(
                    "MEXC v3 rejected subscription " + channel + " (code " + code + ")"));
      }
      return;
    }
    LOG.debug("MEXC v3 command ack: {}", message);
  }

  /** Decodes a binary push and routes it as canonical JSON to the matching channel. */
  protected void handleBinaryPush(byte[] payload) {
    try {
      PushDataV3ApiWrapper wrapper = MexcV3ProtoCodec.decode(payload);
      handleMessage(MexcV3ProtoCodec.toJson(wrapper));
    } catch (InvalidProtocolBufferException e) {
      LOG.error(
          "MEXC v3 binary push is not a valid PushDataV3ApiWrapper; dropping {} bytes",
          payload.length,
          e);
    }
  }

  @Override
  protected String getChannelNameFromMessage(String message) throws IOException {
    JsonNode node = objectMapper.readTree(message);
    String channel = node.path("channel").asText(null);
    return channel == null ? "" : channel;
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) {
    return "{\"method\":\"SUBSCRIPTION\",\"params\":[\"" + channelName + "\"]}";
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) {
    return "{\"method\":\"UNSUBSCRIPTION\",\"params\":[\"" + channelName + "\"]}";
  }

  /** MEXC keepalive is a JSON text PING, not a WebSocket control frame. */
  @Override
  protected void handleIdle(ChannelHandlerContext ctx) {
    sendMessage(PING_MESSAGE);
  }

  /**
   * Rejects new wire subscriptions beyond the per-connection cap instead of silently exceeding
   * it, while still serving additional consumers of channels that are already subscribed (the
   * cap governs reserved distinct channels, not observers of an existing shared stream).
   *
   * <p>The slot is reserved atomically at creation time: the base registers the wire subscription
   * only when the first consumer subscribes, so counting the cap against the base's registered
   * channels would let callers create observables for more than 30 distinct channels (or race
   * concurrent creations) and exceed the cap once everything subscribes. Reservation and the
   * cap check happen under one lock on {@link #sharedChannels}.
   *
   * <p>Private channels ({@code spot@private.*}) are rejected when the connection carries no
   * listen key: without one the server cannot authorize them, so instead of an observable that
   * silently never emits, subscribers get an immediate {@link ExchangeSecurityException}.
   */
  @Override
  public Observable<String> subscribeChannel(String channelName, Object... args) {
    if (channelName.startsWith("spot@private.") && !uri.toString().contains("listenKey=")) {
      return Observable.error(
          new ExchangeSecurityException(
              "MEXC Spot v3 private channel " + channelName + " requires a listen key; "
                  + "configure an API key before connecting"));
    }
    Observable<String> shared = sharedChannels.get(channelName);
    if (shared != null) {
      // Cache hit: the wire subscription already exists, so no new wire subscription is needed
      // and the per-connection cap does not apply.
      return shared;
    }
    synchronized (sharedChannels) {
      shared = sharedChannels.get(channelName);
      if (shared != null) {
        return shared;
      }
      if (sharedChannels.size() >= MAX_SUBSCRIPTIONS_PER_CONNECTION) {
        return Observable.error(
            new ExchangeException(
                "MEXC Spot v3 allows at most "
                    + MAX_SUBSCRIPTIONS_PER_CONNECTION
                    + " subscriptions per connection; disconnect and reconnect to rotate"));
      }
      AtomicInteger consumers = new AtomicInteger();
      java.util.concurrent.atomic.AtomicReference<Observable<String>> holder =
          new java.util.concurrent.atomic.AtomicReference<>();
      Observable<String> channelObservable =
          super.subscribeChannel(channelName, args)
              .doOnSubscribe(s -> consumers.incrementAndGet())
              .doOnDispose(
                  () -> {
                    if (consumers.decrementAndGet() == 0) {
                      synchronized (sharedChannels) {
                        // Precise removal: a fresh wrapper may have been installed since the
                        // last consumer left; removing by key alone would evict that new
                        // subscription and leave its wire subscription unrouted.
                        sharedChannels.computeIfPresent(
                            channelName, (key, current) -> current == holder.get() ? null : current);
                      }
                    }
                  });
      holder.set(channelObservable);
      sharedChannels.put(channelName, channelObservable);
      return channelObservable;
    }
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshaker, WebSocketClientHandler.WebSocketMessageHandler handler) {
    return new MexcV3WebSocketClientHandler(handshaker, handler);
  }

  /**
   * Delivers binary frames to {@link #handleBinaryPush}; every other frame type (text, control
   * frames, handshake responses) is delegated to the stock handler so reconnect and idle behavior
   * is preserved.
   */
  final class MexcV3WebSocketClientHandler extends NettyWebSocketClientHandler {

    MexcV3WebSocketClientHandler(
        WebSocketClientHandshaker handshaker, WebSocketMessageHandler handler) {
      super(handshaker, handler);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof BinaryWebSocketFrame) {
        BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
        byte[] payload = new byte[frame.content().readableBytes()];
        frame.content().readBytes(payload);
        handleBinaryPush(payload);
      } else {
        super.channelRead0(ctx, msg);
      }
    }
  }
}
