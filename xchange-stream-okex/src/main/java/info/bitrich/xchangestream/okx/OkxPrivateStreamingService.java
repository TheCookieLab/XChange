package info.bitrich.xchangestream.okx;

import static info.bitrich.xchangestream.core.StreamingExchange.*;
import static info.bitrich.xchangestream.okx.OkxStreamingService.SUBSCRIBE;
import static info.bitrich.xchangestream.okx.OkxStreamingService.UNSUBSCRIBE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.okx.dto.OkxLoginMessage;
import info.bitrich.xchangestream.okx.dto.OkxSubscribeMessage;
import info.bitrich.xchangestream.okx.dto.OkxSubscriptionTopic;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.trade.OkxAmendOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams.OkxCancelOrderParams;
import org.knowm.xchange.service.BaseParamsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkxPrivateStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(OkxPrivateStreamingService.class);

  public static final String USER_ORDER_CHANGES = "orders";
  public static final String USER_POSITION_CHANGES = "positions";
  public static final String PLACE_ORDER = "order";
  public static final String CHANGE_ORDER = "amend-order";
  public static final String CANCEL_ORDER = "cancel-order";
  private static final String LOGIN_SIGN_METHOD = "GET";
  private static final String LOGIN_SIGN_REQUEST_PATH = "/users/self/verify";
  @Getter private volatile boolean loginDone = false;
  private final Observable<Long> pingPongSrc = Observable.interval(15, 15, TimeUnit.SECONDS);
  private Disposable pingPongSubscription;
  private final ExchangeSpecification exchangeSpecification;
  private volatile boolean needToResubscribeChannels = false;
  private final OkxExchange okxExchange;

  public OkxPrivateStreamingService(
      String privateApiUrl, ExchangeSpecification exchangeSpecification, OkxExchange okxExchange) {
    super(
        privateApiUrl,
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
    this.exchangeSpecification = exchangeSpecification;
    this.okxExchange = okxExchange;
  }

  @Override
  public Completable connect() {
    loginDone = exchangeSpecification.getApiKey() == null;
    Completable conn = super.connect();
    return conn.andThen(
        (CompletableSource)
            (completable) -> {
              try {
                login();
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

  public void login() throws JsonProcessingException {
    Mac mac;
    try {
      mac = Mac.getInstance(BaseParamsDigest.HMAC_SHA_256);
      final SecretKey secretKey =
          new SecretKeySpec(
              exchangeSpecification.getSecretKey().getBytes(StandardCharsets.UTF_8),
              BaseParamsDigest.HMAC_SHA_256);
      mac.init(secretKey);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new ExchangeException("Invalid API secret", e);
    }
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String toSign = timestamp + LOGIN_SIGN_METHOD + LOGIN_SIGN_REQUEST_PATH;
    String sign =
        Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));

    OkxLoginMessage message = new OkxLoginMessage();
    String passphrase =
        exchangeSpecification.getExchangeSpecificParametersItem("passphrase").toString();
    OkxLoginMessage.LoginArg loginArg =
        new OkxLoginMessage.LoginArg(
            exchangeSpecification.getApiKey(), passphrase, timestamp, sign);
    message.getArgs().add(loginArg);
    this.sendMessage(objectMapper.writeValueAsString(message));
  }

  public void pingPongDisconnectIfConnected() {
    if (pingPongSubscription != null && !pingPongSubscription.isDisposed()) {
      pingPongSubscription.dispose();
    }
  }

  private OkxSubscriptionTopic getTopic(String channelName) {
    if (channelName.contains(USER_ORDER_CHANGES)) {
      return new OkxSubscriptionTopic(
          USER_ORDER_CHANGES, OkxInstType.ANY, null, channelName.replace(USER_ORDER_CHANGES, ""));
    } else {
      if ((channelName.contains(USER_POSITION_CHANGES))) {
        return new OkxSubscriptionTopic(
            USER_POSITION_CHANGES,
            OkxInstType.ANY,
            null,
            channelName.replace(USER_POSITION_CHANGES, ""));
      } else {
        return null;
      }
    }
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    return channelName;
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
        LOG.debug("Received pong message: {}", message);
        return;
      }
      LOG.error("Error parsing incoming message to JSON: {}", message);
      return;
    }
    // Retry after a successful login.
    if (jsonNode.has("event")) {
      String event = jsonNode.get("event").asText();
      if ("login".equals(event)) {
        String code = jsonNode.path("code").asText();
        loginDone = code.isEmpty() || "0".equals(code);
        if (loginDone && needToResubscribeChannels) {
          this.resubscribeChannels();
          needToResubscribeChannels = false;
        }
        return;
      }
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
    if (message.has("id")) {
      return message.get("id").asText();
    } else {
      if (message.has("arg")) {
        if (message.get("arg").has("channel") && message.get("arg").has("instId")) {
          channelName =
              message.get("arg").get("channel").asText()
                  + message.get("arg").get("instId").asText();
        }
      }
    }
    return channelName;
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    if (args != null && args.length > 0) {
      String method = args[0].toString();
      switch (method) {
        case PLACE_ORDER:
          {
            OkxOrderRequest orderPayload;
            if (args[1] instanceof LimitOrder) {
              LimitOrder limitOrder = (LimitOrder) args[1];
              orderPayload =
                  OkxAdapters.adaptOrder(
                      limitOrder, okxExchange.getExchangeMetaData(), okxExchange.accountLevel);
            } else {
              MarketOrder marketOrder = (MarketOrder) args[1];
              orderPayload =
                  OkxAdapters.adaptOrder(
                      marketOrder, okxExchange.getExchangeMetaData(), okxExchange.accountLevel);
            }
            OkxSubscribeMessage<OkxOrderRequest> payload =
                new OkxSubscribeMessage<>(
                    channelName, PLACE_ORDER, Collections.singletonList(orderPayload));
            return objectMapper.writeValueAsString(payload);
          }
        case CHANGE_ORDER:
          {
            LimitOrder limitOrder = (LimitOrder) args[1];
            OkxAmendOrderRequest orderChangePayload =
                OkxAdapters.adaptAmendOrder(limitOrder, okxExchange.getExchangeMetaData());
            OkxSubscribeMessage<OkxAmendOrderRequest> payload =
                new OkxSubscribeMessage<>(
                    channelName, CHANGE_ORDER, Collections.singletonList(orderChangePayload));
            return objectMapper.writeValueAsString(payload);
          }
        case CANCEL_ORDER:
          {
            OkxCancelOrderParams params = (OkxCancelOrderParams) args[1];
            OkxCancelOrderRequest orderChangePayload =
                OkxCancelOrderRequest.builder()
                    .instIdCode(OkxAdapters.instrumentToInstrumentCode(params.instrument))
                    .orderId(params.orderId)
                    .clientOrderId(params.getUserReference())
                    .build();
            OkxSubscribeMessage<OkxCancelOrderRequest> payload =
                new OkxSubscribeMessage<>(
                    channelName, CANCEL_ORDER, Collections.singletonList(orderChangePayload));
            return objectMapper.writeValueAsString(payload);
          }
      }
    }
    return objectMapper.writeValueAsString(
        new OkxSubscribeMessage<>("", SUBSCRIBE, Collections.singletonList(getTopic(channelName))));
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    OkxSubscriptionTopic subscriptionTopic = getTopic(channelName);
    if (subscriptionTopic != null) {
      return objectMapper.writeValueAsString(
          new OkxSubscribeMessage<>("", UNSUBSCRIBE, Collections.singletonList(subscriptionTopic)));
    }
    return null;
  }

  @Override
  public void resubscribeChannels() {
    needToResubscribeChannels = true;
    if (loginDone) {
      super.resubscribeChannels();
    }
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshake, WebSocketClientHandler.WebSocketMessageHandler handler) {
    // Tag every message with the generation of the connection it arrived on; the message-handling
    // boundary drops messages from superseded connections (e.g. after a reconnect).
    long connectionGeneration = getGeneration();
    return new NettyWebSocketClientHandler(
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
   * @return whether any private channel subscription is currently active (for example order or
   *     position streams)
   */
  public boolean hasActiveChannels() {
    return !channels.isEmpty();
  }
}
