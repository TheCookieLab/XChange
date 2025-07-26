package info.bitrich.xchangestream.okex;

import static info.bitrich.xchangestream.okex.OkexStreamingService.SUBSCRIBE;
import static info.bitrich.xchangestream.okex.OkexStreamingService.UNSUBSCRIBE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.okex.dto.OkexLoginMessage;
import info.bitrich.xchangestream.okex.dto.OkexSubscribeMessage;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.service.BaseParamsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkexPrivateStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(OkexPrivateStreamingService.class);

  public static final String USER_ORDER_CHANGES = "orders";
  public static final String USER_POSITION_CHANGES = "positions";
  private static final String LOGIN_SIGN_METHOD = "GET";
  private static final String LOGIN_SIGN_REQUEST_PATH = "/users/self/verify";
  @Getter private volatile boolean loginDone = false;
  private final Observable<Long> pingPongSrc = Observable.interval(15, 15, TimeUnit.SECONDS);
  private Disposable pingPongSubscription;
  private final ExchangeSpecification exchangeSpecification;
  private volatile boolean needToResubscribeChannels = false;

  public OkexPrivateStreamingService(
      String privateApiUrl, ExchangeSpecification exchangeSpecification) {
    super(privateApiUrl);
    this.exchangeSpecification = exchangeSpecification;
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

    OkexLoginMessage message = new OkexLoginMessage();
    String passphrase =
        exchangeSpecification.getExchangeSpecificParametersItem("passphrase").toString();
    OkexLoginMessage.LoginArg loginArg =
        new OkexLoginMessage.LoginArg(
            exchangeSpecification.getApiKey(), passphrase, timestamp, sign);
    message.getArgs().add(loginArg);
    this.sendMessage(objectMapper.writeValueAsString(message));
  }

  public void pingPongDisconnectIfConnected() {
    if (pingPongSubscription != null && !pingPongSubscription.isDisposed()) {
      pingPongSubscription.dispose();
    }
  }

  private OkexSubscribeMessage.SubscriptionTopic getTopic(String channelName) {
    if (channelName.contains(USER_ORDER_CHANGES)) {
      return new OkexSubscribeMessage.SubscriptionTopic(
          USER_ORDER_CHANGES, OkexInstType.ANY, null, channelName.replace(USER_ORDER_CHANGES, ""));
    } else {
      if ((channelName.contains(USER_POSITION_CHANGES))) {
        return new OkexSubscribeMessage.SubscriptionTopic(
            USER_POSITION_CHANGES, OkexInstType.ANY, null, channelName.replace(USER_POSITION_CHANGES, ""));
      } else {
        throw new NotYetImplementedForExchangeException(
            "ChannelName: "
                + channelName
                + " has not implemented yet on "
                + this.getClass().getSimpleName());
      }
    }
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
    // Retry after a successful login
    if (jsonNode.has("event")) {
      String event = jsonNode.get("event").asText();
      if ("login".equals(event)) {
        loginDone = true;
        if (needToResubscribeChannels) {
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
    if (message.has("arg")) {
      if (message.get("arg").has("channel") && message.get("arg").has("instId")) {
        channelName =
            message.get("arg").get("channel").asText() + message.get("arg").get("instId").asText();
      }
    }
    return channelName;
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    return objectMapper.writeValueAsString(
        new OkexSubscribeMessage(SUBSCRIBE, Collections.singletonList(getTopic(channelName))));
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    return objectMapper.writeValueAsString(
        new OkexSubscribeMessage(UNSUBSCRIBE, Collections.singletonList(getTopic(channelName))));
  }

  @Override
  public void resubscribeChannels() {
    needToResubscribeChannels = true;
    if (loginDone) {
      super.resubscribeChannels();
    }
  }
}
