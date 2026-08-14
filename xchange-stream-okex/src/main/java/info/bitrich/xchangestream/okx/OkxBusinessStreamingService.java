package info.bitrich.xchangestream.okx;

import static info.bitrich.xchangestream.core.StreamingExchange.*;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.okx.dto.OkxSubscribeMessage;
import info.bitrich.xchangestream.okx.dto.OkxSubscriptionTopic;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import org.knowm.xchange.ExchangeSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkxBusinessStreamingService extends JsonNettyStreamingService {
  private static final Logger LOG = LoggerFactory.getLogger(OkxBusinessStreamingService.class);

  protected static final String SUBSCRIBE = "subscribe";
  protected static final String UNSUBSCRIBE = "unsubscribe";
  @Setter private WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler = null;
  private final Observable<Long> pingPongSrc = Observable.interval(15, 15, TimeUnit.SECONDS);
  private Disposable pingPongSubscription;

  public OkxBusinessStreamingService(String apiUrl, ExchangeSpecification exchangeSpecification) {
    super(
        apiUrl,
        65536,
        (Duration)
            Optional.ofNullable(
                    (Duration)
                        exchangeSpecification.getExchangeSpecificParametersItem(
                            WS_CONNECTION_TIMEOUT))
                .orElse(DEFAULT_CONNECTION_TIMEOUT),
        (Duration)
            Optional.ofNullable(
                    (Duration)
                        exchangeSpecification.getExchangeSpecificParametersItem(WS_RETRY_DURATION))
                .orElse(DEFAULT_RETRY_DURATION),
        (Integer)
            Optional.ofNullable(
                    (Integer)
                        exchangeSpecification.getExchangeSpecificParametersItem(WS_IDLE_TIMEOUT))
                .orElse(DEFAULT_IDLE_TIMEOUT));
  }

  @Override
  public Completable connect() {
    Completable conn = super.connect();
    return conn.andThen(
        (CompletableSource)
            (completable) -> {
              try {
                if (pingPongSubscription != null && !pingPongSubscription.isDisposed()) {
                  pingPongSubscription.dispose();
                }
                pingPongSubscription = pingPongSrc.subscribe(o -> this.sendMessage("ping"));
                completable.onComplete();
              } catch (Exception e) {
                completable.onError(e);
              }
            });
  }

  @Override
  public void messageHandler(String message) {
    LOG.debug("Received message: {}", message);
    JsonNode jsonNode;

    // Parse incoming message to JSON
    try {
      jsonNode = objectMapper.readTree(message);
    } catch (IOException e) {
      if ("pong".equals(message)) {
        // ping pong message
        return;
      }
      LOG.error("Error parsing incoming message to JSON: {}", message);
      return;
    }
    if (jsonNode.get("event") != null && jsonNode.get("event").asText().equals("subscribe")) {
      return;
    }
    if (processArrayMessageSeparately() && jsonNode.isArray()) {
      // In case of array - handle every message separately.
      for (JsonNode node : jsonNode) {
        handleMessage(node);
      }
    } else {
      handleMessage(jsonNode);
    }
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    String channelName = "";
    if (message.has("arg")) {
      if (message.get("arg").has("channel") && message.get("arg").has("instId")) {
        channelName =
            message.get("arg").get("channel").asText()
                + "-"
                + message.get("arg").get("instId").asText();
      }
    }
    return channelName;
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    return objectMapper.writeValueAsString(
        new OkxSubscribeMessage<>("", SUBSCRIBE, Collections.singletonList(getTopic(channelName))));
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    return objectMapper.writeValueAsString(
        new OkxSubscribeMessage<>(
            "", UNSUBSCRIBE, Collections.singletonList(getTopic(channelName))));
  }

  private OkxSubscriptionTopic getTopic(String channelName) {
    int separatorIndex = channelName.indexOf('-');
    String okxChannels = channelName.substring(0, separatorIndex);
    String instrument = channelName.substring(separatorIndex + 1);
    return new OkxSubscriptionTopic(okxChannels, null, null, instrument);
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshake, WebSocketClientHandler.WebSocketMessageHandler handler) {
    LOG.info("Registering OkxWebSocketClientHandler");
    // Tag every message with the generation of the connection it arrived on; the message-handling
    // boundary drops messages from superseded connections (e.g. after a reconnect).
    long connectionGeneration = getGeneration();
    return new OkxWebSocketClientHandler(
        handshake, message -> handleMessageWithGeneration(connectionGeneration, message));
  }

  /**
   * Message-handling boundary that drops messages arriving from a stale connection generation.
   *
   * <p>Every {@link #connect()} establishes a new connection generation (see {@link
   * #getGeneration()}). The websocket client handler captures the generation it was created with
   * and routes every message through this method, so a late response delivered by a superseded
   * socket after a reconnect is rejected instead of being processed.
   *
   * @param messageGeneration the generation of the connection the message arrived on
   * @param message the raw websocket message
   * @return {@code true} if the message belonged to the current generation and was forwarded to
   *     {@link #messageHandler(String)}; {@code false} if it was dropped as stale
   */
  protected boolean handleMessageWithGeneration(long messageGeneration, String message) {
    if (messageGeneration != getGeneration()) {
      LOG.debug(
          "Dropping stale message from connection generation {} (current generation {})",
          messageGeneration,
          getGeneration());
      return false;
    }
    messageHandler(message);
    return true;
  }

  /**
   * @return whether any business channel subscription is currently active (for example candle-stick
   *     channels)
   */
  public boolean hasActiveChannels() {
    return !channels.isEmpty();
  }

  /**
   * Custom client handler in order to execute an external, user-provided handler on channel events.
   */
  class OkxWebSocketClientHandler extends NettyWebSocketClientHandler {

    public OkxWebSocketClientHandler(
        WebSocketClientHandshaker handshake, WebSocketMessageHandler handler) {
      super(handshake, handler);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      super.channelInactive(ctx);
      if (channelInactiveHandler != null) {
        channelInactiveHandler.onMessage("WebSocket Client disconnected!");
      }
    }
  }

  public void pingPongDisconnectIfConnected() {
    if (pingPongSubscription != null && !pingPongSubscription.isDisposed()) {
      pingPongSubscription.dispose();
    }
  }
}
