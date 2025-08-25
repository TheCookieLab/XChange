package info.bitrich.xchangestream.bybit;

import static org.knowm.xchange.bybit.BybitAdapters.adaptMarketOrder;
import static org.knowm.xchange.utils.DigestUtils.bytesToHex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dto.BybitSubscribeMessage;
import dto.trade.BybitOrderMessage;
import dto.trade.BybitOrderMessage.Header;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientCompressionAllowClientNoContextHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitAdapters;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.trade.BybitPlaceOrderPayload;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.BaseParamsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BybitUserTradeService extends JsonNettyStreamingService {
  private static final Logger LOG = LoggerFactory.getLogger(BybitUserTradeService.class);
  private final ExchangeSpecification spec;
  @Getter
  private boolean isAuthorized = false;
  private String connId;

  public BybitUserTradeService(String apiUrl, ExchangeSpecification spec) {
    super(apiUrl);
    this.spec = spec;
  }

  @Override
  public Completable connect() {
    Completable conn = super.connect();
    return conn.andThen(
        (CompletableSource)
            (completable) -> {
              LOG.info("Connect to BybitUserTradeStream with auth");
              login();
              completable.onComplete();
            });
  }

  private void login() {
    String key = spec.getApiKey();
    long expires = Instant.now().toEpochMilli() + 10000;
    String _val = "GET/realtime" + expires;
    try {
      Mac mac = Mac.getInstance(BaseParamsDigest.HMAC_SHA_256);
      final SecretKey secretKey =
          new SecretKeySpec(
              spec.getSecretKey().getBytes(StandardCharsets.UTF_8), BaseParamsDigest.HMAC_SHA_256);
      mac.init(secretKey);
      String signature = bytesToHex(mac.doFinal(_val.getBytes(StandardCharsets.UTF_8)));
      List<String> args =
          Stream.of(key, String.valueOf(expires), signature).collect(Collectors.toList());
      String message = objectMapper.writeValueAsString(new BybitSubscribeMessage("auth", args));
      this.sendMessage(message);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new ExchangeException("Invalid API secret", e);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void messageHandler(String message) {
    LOG.debug("Received message: {}", message);
    JsonNode jsonNode;
    try {
      jsonNode = objectMapper.readTree(message);
    } catch (IOException e) {
      LOG.error("Error parsing incoming message to JSON: {}", message);
      return;
    }
    if (jsonNode.has("op") && jsonNode.get("op").asText().equals("auth")) {
      if (jsonNode.has("retMsg") && jsonNode.get("retMsg").asText().equals("OK")) {
        connId = jsonNode.get("connId").asText();
        isAuthorized = true;
        LOG.debug("Successfully authenticated to trade URI");
        return;
      } else {
        throw new ExchangeException(jsonNode.get("retMsg").asText());
      }
    }
    handleMessage(jsonNode);
  }

  @Override
  protected WebSocketClientExtensionHandler getWebSocketClientExtensionHandler() {
    return WebSocketClientCompressionAllowClientNoContextHandler.INSTANCE;
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) throws IOException {
    return message.get("op").asText()+message.get("reqId").asText();
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
      return channelName;
  }

  Pattern p = Pattern.compile("[a-z.]+|\\d+");

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    MarketOrder marketOrders = (MarketOrder) args[0];
    Header header = new Header(String.valueOf(System.currentTimeMillis()), "5000", "");
    BybitCategory category = BybitAdapters.getCategory(marketOrders.getInstrument());
    List<BybitPlaceOrderPayload> bybitPlaceOrderPayload = List.of(adaptMarketOrder(marketOrders, category));
    // split to reqId and channelName
    Matcher m = p.matcher(channelName);
    ArrayList<String> allMatches = new ArrayList<>();
    while (m.find()) {
      allMatches.add(m.group());
    }
    channelName =  allMatches.get(0);
    String reqId = allMatches.get(1);
    BybitOrderMessage bybitOrderMessage = new BybitOrderMessage(reqId, header, channelName, bybitPlaceOrderPayload);
    return objectMapper.writeValueAsString(bybitOrderMessage);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    return null;
  }
}
