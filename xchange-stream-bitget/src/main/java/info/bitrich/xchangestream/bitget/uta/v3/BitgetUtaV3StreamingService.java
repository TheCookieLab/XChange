package info.bitrich.xchangestream.bitget.uta.v3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.dto.common.Operation;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsRequest;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Netty transport for the Bitget UTA v3 WebSocket protocol.
 *
 * <p>Differences from the classic v2 service:
 *
 * <ul>
 *   <li>v3 endpoints ({@code wss://ws.bitget.com/v3/ws/public|private});
 *   <li>heartbeat is the text frame {@code "ping"} (the server answers {@code "pong"} and closes
 *       the connection after two minutes without one), not a binary ping;
 *   <li>push envelopes carry {@code action: snapshot|update} and acknowledgements carry {@code
 *       event}; there is no v2-style {@code messageType} discriminator;
 *   <li>every (re)connect claims a fresh {@link #getConnectionGeneration() connection generation}
 *       and the per-connection message gate ({@link
 *       #gateByConnectionGeneration(AtomicLong, WebSocketMessageHandler)}) drops frames from a
 *       stale connection before they can mutate current state.
 * </ul>
 *
 * @since 5.1.0
 */
@Slf4j
public class BitgetUtaV3StreamingService extends NettyStreamingService<BitgetUtaV3WsNotification> {

  protected final ObjectMapper objectMapper = Config.getInstance().getObjectMapper();

  protected final AtomicLong connectionGeneration = new AtomicLong();

  /** Generation stamp of the message callback of the most recently opened connection. */
  private volatile AtomicLong messageConnectionStamp = new AtomicLong();

  /** One shared channel stream per subscription id; see {@link #sharedChannel}. */
  private final ConcurrentMap<String, Observable<BitgetUtaV3WsNotification>> sharedChannels =
      new ConcurrentHashMap<>();

  public BitgetUtaV3StreamingService(String apiUri) {
    super(apiUri, Integer.MAX_VALUE);
  }

  /**
   * Claims a fresh connection generation when a (re)connect attempt starts, before any socket is
   * open. Combined with the per-connection message gate ({@link
   * #gateByConnectionGeneration(AtomicLong, WebSocketMessageHandler)}), a frame dispatched late by
   * a previous connection's handler can never pass the gate: its stamp predates the new
   * generation even in the window between the new socket opening and its {@code resubscribe}.
   */
  @Override
  protected Completable openConnection() {
    connectionGeneration.incrementAndGet();
    return super.openConnection();
  }

  @Override
  protected String getChannelNameFromMessage(BitgetUtaV3WsNotification message) {
    return message.getChannel() == null ? null : message.getChannel().toSubscriptionId();
  }

  /**
   * @param channelName ignored; the subscription id is derived from the channel
   * @param args single {@link BitgetUtaV3Channel}
   */
  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    BitgetUtaV3Channel channel = toChannel(args);
    BitgetUtaV3WsRequest request =
        BitgetUtaV3WsRequest.builder().operation(Operation.SUBSCRIBE).channel(channel).build();
    return objectMapper.writeValueAsString(request);
  }

  /**
   * @param channelName ignored; the subscription id is derived from the channel
   * @param args single {@link BitgetUtaV3Channel}
   */
  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    BitgetUtaV3Channel channel = toChannel(args);
    BitgetUtaV3WsRequest request =
        BitgetUtaV3WsRequest.builder().operation(Operation.UNSUBSCRIBE).channel(channel).build();
    return objectMapper.writeValueAsString(request);
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    return toChannel(args).toSubscriptionId();
  }

  /**
   * One underlying channel subscription per subscription id, shared by all concurrent callers.
   *
   * <p>{@link NettyStreamingService#subscribeChannel} registers a single emitter per id and
   * discards later emitters for the same id; sharing the returned observable here lets every
   * caller's pipeline receive pushes, and ref-counted teardown keeps one subscriber's dispose from
   * unsubscribing a channel other subscribers still use.
   */
  protected Observable<BitgetUtaV3WsNotification> sharedChannel(BitgetUtaV3Channel channel) {
    return sharedChannels.computeIfAbsent(
        channel.toSubscriptionId(), id -> subscribeChannel(null, channel).share());
  }

  /**
   * Bumps the connection generation on every (re)connect and binds it to the message callback of
   * the connection being resubscribed. v3 acknowledgements are correlated to the generation they
   * were requested under; late frames from a previous connection are dropped by the per-connection
   * gate ({@link #gateByConnectionGeneration(AtomicLong, WebSocketMessageHandler)}) and can never
   * mutate the current subscription state.
   */
  @Override
  public void resubscribeChannels() {
    connectionGeneration.incrementAndGet();
    stampCurrentConnection();
    super.resubscribeChannels();
  }

  /** Binds the current connection generation to the most recently opened connection's gate. */
  void stampCurrentConnection() {
    messageConnectionStamp.set(connectionGeneration.get());
  }

  /**
   * Wraps the service message handler with a per-connection generation gate.
   *
   * <p>{@code connectionStamp} belongs to exactly one socket: it is bound ({@link
   * #stampCurrentConnection()}) when that connection is resubscribed, so a frame delivered by a
   * previous connection's callback carries the generation that connection was resubscribed under.
   * Comparing that fixed stamp against the current generation at dispatch time rejects late frames
   * from stale connections — including a {@code login} acknowledgement from the previous socket
   * arriving after the reconnect — which a shared scalar could not distinguish. An unbound stamp
   * (zero) belongs to the newest connection before its resubscribe, which is by definition the
   * current one.
   */
  WebSocketClientHandler.WebSocketMessageHandler gateByConnectionGeneration(
      AtomicLong connectionStamp,
      WebSocketClientHandler.WebSocketMessageHandler delegate) {
    return message -> {
      long generation = connectionStamp.get();
      if (generation == 0 || isCurrentGeneration(generation)) {
        delegate.onMessage(message);
      } else {
        log.warn(
            "Ignoring message from stale connection generation {} (current {}): {}",
            generation,
            getConnectionGeneration(),
            message);
      }
    };
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshaker,
      WebSocketClientHandler.WebSocketMessageHandler handler) {
    AtomicLong connectionStamp = new AtomicLong();
    messageConnectionStamp = connectionStamp;
    return new GatedNettyWebSocketClientHandler(
        handshaker, gateByConnectionGeneration(connectionStamp, handler));
  }

  /**
   * Netty handler routing every inbound frame through the {@link
   * #gateByConnectionGeneration(AtomicLong, WebSocketMessageHandler) per-connection generation
   * gate}, so a frame delivered late by a previous connection's callback never reaches the
   * message handler. Kept as a subclass solely to access the base {@code
   * NettyWebSocketClientHandler} constructor from this package.
   */
  private final class GatedNettyWebSocketClientHandler extends NettyWebSocketClientHandler {

    private GatedNettyWebSocketClientHandler(
        WebSocketClientHandshaker handshaker, WebSocketMessageHandler handler) {
      super(handshaker, handler);
    }
  }

  /** Current connection generation; increments on every (re)connect attempt. */
  public long getConnectionGeneration() {
    return connectionGeneration.get();
  }

  /** Whether {@code generation} belongs to the current connection. */
  public boolean isCurrentGeneration(long generation) {
    return connectionGeneration.get() == generation;
  }

  @Override
  public void messageHandler(String message) {
    log.debug("Received message: {}", message);

    if ("pong".equals(message)) {
      log.trace("Heartbeat pong received");
      return;
    }

    try {
      JsonNode jsonNode = objectMapper.readTree(message);
      BitgetUtaV3WsNotification notification;
      if (jsonNode.has("event")) {
        notification = objectMapper.treeToValue(jsonNode, BitgetUtaV3EventNotification.class);
      } else {
        notification = objectMapper.treeToValue(jsonNode, BitgetUtaV3WsNotification.class);
      }
      handleMessage(notification);
    } catch (IOException e) {
      log.error("Error parsing incoming message to JSON: {}", message);
      log.error(e.getMessage(), e);
    }
  }

  @Override
  protected void handleMessage(BitgetUtaV3WsNotification message) {
    // events (acks) are handled before channel routing; pushes go to the subscriber streams
    if (message instanceof BitgetUtaV3EventNotification) {
      handleEventNotification((BitgetUtaV3EventNotification) message);
      return;
    }
    super.handleMessage(message);
  }

  /**
   * Delivers an acknowledgement failure to the affected subscriber streams.
   *
   * <p>Subscribe/unsubscribe acknowledgements carry the channel they concern ({@code arg}); on an
   * error ack the matching {@link Subscription} emitter is terminated with the failure so the
   * caller observes the rejection instead of waiting on a push that will never arrive.
   */
  protected void handleEventNotification(BitgetUtaV3EventNotification notification) {
    if (notification.getEvent() == BitgetUtaV3EventNotification.Event.ERROR
        || (notification.getCode() != null
            && !notification.getCode().isEmpty()
            && !"0".equals(notification.getCode()))) {
      String failure =
          String.format(
              "Bitget UTA v3 WebSocket %s failed: code=%s, msg=%s, channel=%s",
              notification.getEvent(),
              notification.getCode(),
              notification.getMessage(),
              notification.getChannel());
      log.warn(failure);
      if (notification.getChannel() != null) {
        String subscriptionId = notification.getChannel().toSubscriptionId();
        Subscription subscription = channels.remove(subscriptionId);
        if (subscription != null) {
          sharedChannels.remove(subscriptionId);
          subscription.getEmitter().tryOnError(new ExchangeException(failure));
        }
      }
      return;
    }
    log.debug(
        "Bitget UTA v3 WebSocket {} acknowledged: channel={}, connId={}",
        notification.getEvent(),
        notification.getChannel(),
        notification.getConnectionId());
  }

  /**
   * Terminates every registered channel stream with {@code error}; used when the connection fails
   * as a whole (e.g. a rejected private login) so subscribers never hang on it.
   */
  protected void failAllChannels(Throwable error) {
    for (Entry<String, Subscription> entry : channels.entrySet()) {
      entry.getValue().getEmitter().tryOnError(error);
    }
    // drop the cached shared observables so a retry after reconnect builds fresh ones instead of
    // reusing observables whose emitters were just terminated
    sharedChannels.clear();
  }

  /** UTA v3 keeps the connection alive with the text frame {@code "ping"}. */
  @Override
  protected void handleIdle(ChannelHandlerContext ctx) {
    ctx.writeAndFlush(new TextWebSocketFrame("ping"));
  }

  private static BitgetUtaV3Channel toChannel(Object... args) {
    return (BitgetUtaV3Channel) args[0];
  }
}
