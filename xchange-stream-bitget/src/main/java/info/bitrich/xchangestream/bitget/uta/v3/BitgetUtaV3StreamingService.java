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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

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
 *   <li>every (re)connect bumps a {@link #getConnectionGeneration() connection generation} so
 *       acknowledgements from an earlier connection cannot mutate current state.
 * </ul>
 *
 * @since 5.1.0
 */
@Slf4j
public class BitgetUtaV3StreamingService extends NettyStreamingService<BitgetUtaV3WsNotification> {

  protected final ObjectMapper objectMapper = Config.getInstance().getObjectMapper();

  protected final AtomicLong connectionGeneration = new AtomicLong();

  public BitgetUtaV3StreamingService(String apiUri) {
    super(apiUri, Integer.MAX_VALUE);
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
   * Bumps the connection generation on every (re)connect. v3 acknowledgements are correlated to the
   * generation they were requested under; late acks from a previous connection are dropped by
   * {@link #isCurrentGeneration(long)} and can never mutate the current subscription state.
   */
  @Override
  public void resubscribeChannels() {
    connectionGeneration.incrementAndGet();
    super.resubscribeChannels();
  }

  /** Current connection generation; increments on every (re)connect. */
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

  /** Logs ack outcomes; subscribe/unsubscribe/login errors are surfaced, not silently ignored. */
  protected void handleEventNotification(BitgetUtaV3EventNotification notification) {
    if (notification.getEvent() == BitgetUtaV3EventNotification.Event.ERROR
        || (notification.getCode() != null
            && !notification.getCode().isEmpty()
            && !"0".equals(notification.getCode()))) {
      log.warn(
          "Bitget UTA v3 WebSocket {} failed: code={}, msg={}, channel={}",
          notification.getEvent(),
          notification.getCode(),
          notification.getMessage(),
          notification.getChannel());
      return;
    }
    log.debug(
        "Bitget UTA v3 WebSocket {} acknowledged: channel={}, connId={}",
        notification.getEvent(),
        notification.getChannel(),
        notification.getConnectionId());
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
