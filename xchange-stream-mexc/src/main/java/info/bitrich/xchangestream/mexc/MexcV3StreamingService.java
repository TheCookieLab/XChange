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
import java.util.concurrent.atomic.AtomicReference;
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
   *
   * <p>An entry is created when the observable is handed out (so repeated calls for the same
   * channel share one object), but it reserves a per-connection subscription slot only once the
   * first consumer actually subscribes; observables that are never subscribed therefore do not
   * count against the 30-channel cap (see {@link #onFirstConsumer}).
   */
  private final Map<String, ChannelEntry> sharedChannels = new ConcurrentHashMap<>();

  /**
   * Number of distinct channels with at least one active observer — i.e. channels whose wire
   * subscription is currently registered. All transitions happen under the {@link
   * #sharedChannels} lock so the cap is decided atomically.
   */
  private final AtomicInteger subscribedChannelCount = new AtomicInteger();

  /** A channel's shared observable plus the consumer count that decides when it is evicted. */
  private static final class ChannelEntry {
    final AtomicReference<Observable<String>> observable = new AtomicReference<>();
    final AtomicInteger consumers = new AtomicInteger();
  }

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
   * <p>A slot is reserved only when the first consumer of a channel subscribes ({@link
   * #onFirstConsumer}), not when the observable is created: the base registers the wire
   * subscription lazily at first subscribe, so reserving at creation would let callers block the
   * cap with observables they never subscribe. The cap check and the first-subscribe transition
   * happen under one lock on {@link #sharedChannels}, so concurrent first subscriptions cannot
   * both pass the check and exceed the cap.
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
    ChannelEntry entry = sharedChannels.get(channelName);
    if (entry != null) {
      // Cache hit: the wire subscription already exists, so no new wire subscription is needed
      // and the per-connection cap does not apply.
      return entry.observable.get();
    }
    synchronized (sharedChannels) {
      entry = sharedChannels.get(channelName);
      if (entry != null) {
        return entry.observable.get();
      }
      ChannelEntry created = new ChannelEntry();
      // One base instance per channel: its share() is what makes additional consumers join the
      // existing wire subscription instead of opening their own.
      Observable<String> base = super.subscribeChannel(channelName, args);
      Observable<String> channelObservable =
          Observable.defer(
              () -> {
                // The cap is decided before the base is subscribed: on a full connection the
                // observer is failed without ever touching the base, so no wire subscription
                // is registered and nothing has to be unwound.
                if (!onFirstConsumer(channelName, created)) {
                  return Observable.error(
                      new ExchangeException(
                          "MEXC Spot v3 allows at most "
                              + MAX_SUBSCRIPTIONS_PER_CONNECTION
                              + " subscriptions per connection; disconnect and reconnect to rotate"));
                }
                return base.doOnDispose(() -> onLastConsumerDispose(channelName, created));
              });
      created.observable.set(channelObservable);
      sharedChannels.put(channelName, created);
      return channelObservable;
    }
  }

  /**
   * Runs when an observer subscribes to a channel's shared observable. The first observer
   * acquires the per-connection slot; when the cap is already reached the observer is refused
   * ({@code false}) and the channel entry is reset and evicted so a later subscribe builds a
   * fresh wrapper and retries from scratch.
   *
   * @return {@code true} when the slot was acquired, {@code false} when the cap is full
   */
  private boolean onFirstConsumer(String channelName, ChannelEntry entry) {
    if (entry.consumers.incrementAndGet() != 1) {
      return true; // additional observer of an already-subscribed channel
    }
    synchronized (sharedChannels) {
      if (subscribedChannelCount.get() >= MAX_SUBSCRIPTIONS_PER_CONNECTION) {
        entry.consumers.set(0);
        sharedChannels.computeIfPresent(
            channelName, (key, current) -> current == entry ? null : current);
        return false;
      }
      subscribedChannelCount.incrementAndGet();
      return true;
    }
  }

  /**
   * Runs when an observer disposes its subscription. The last observer releases the channel's
   * per-connection slot and evicts the shared entry so a later subscribe builds a fresh wrapper;
   * the base mirror-removes the channel and sends UNSUBSCRIPTION. A concurrent first observer
   * that subscribed between the decrement and the lock keeps the entry alive.
   */
  private void onLastConsumerDispose(String channelName, ChannelEntry entry) {
    if (entry.consumers.get() == 0 || entry.consumers.decrementAndGet() != 0) {
      return; // already released, or other observers remain
    }
    synchronized (sharedChannels) {
      if (entry.consumers.get() != 0) {
        return; // a new observer subscribed while the lock was contended
      }
      subscribedChannelCount.decrementAndGet();
      sharedChannels.computeIfPresent(
          channelName, (key, current) -> current == entry ? null : current);
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
