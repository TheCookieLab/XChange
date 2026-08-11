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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderResult;
import org.knowm.xchange.kucoin.uta.service.UtaApiException;
import org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification;
import org.knowm.xchange.kucoin.uta.service.UtaDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pro WebSocket Add/Cancel Order socket ({@code wss://wsapi.kucoin.com/v1/private}) for UTA.
 *
 * <p>Implements the documented authentication handshake (URL query credentials, then the
 * {@code sessionId + timestamp} challenge signed with the API secret), JSON ping/pong frames,
 * request/response correlation by id, and the no-blind-replay contract: a disconnect fails all
 * pending placements with an explicit unknown-outcome exception and never silently resends them.
 *
 * @see <a href="https://www.kucoin.com/docs-new/3470133w0">Add Order (Pro WebSocket)</a>
 */
public class UtaStreamingTradingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(UtaStreamingTradingService.class);

  private static final String WSS_ENDPOINT = "wss://wsapi.kucoin.com/v1/private";

  private final AtomicLong refCount = new AtomicLong();
  private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
  private final String secretKey;
  private volatile boolean authenticated;
  private Disposable pingSubscription;

  /** @param exchange the UTA-mode exchange whose credentials sign the socket */
  public UtaStreamingTradingService(KucoinExchange exchange) throws IOException {
    super(buildUrl(exchange), 65536);
    this.secretKey = exchange.getExchangeSpecification().getSecretKey();
  }

  private static String buildUrl(KucoinExchange exchange) throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    String secretKey = exchange.getExchangeSpecification().getSecretKey();
    String passphrase =
        (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("passphrase");
    if (apiKey == null || secretKey == null || passphrase == null) {
      throw new IOException("API key, secret and passphrase are required for UTA WS trading");
    }
    long timestamp = System.currentTimeMillis();
    String prehash = apiKey + timestamp;
    String sign = hmacSha256Base64(secretKey, prehash);
    String encryptedPassphrase = UtaDigest.encryptPassphrase(passphrase, secretKey);
    return WSS_ENDPOINT
        + "?apikey="
        + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        + "&timestamp="
        + timestamp
        + "&sign="
        + URLEncoder.encode(sign, StandardCharsets.UTF_8)
        + "&passphrase="
        + URLEncoder.encode(encryptedPassphrase, StandardCharsets.UTF_8)
        + "&enable_ns=true";
  }

  private static String hmacSha256Base64(String secret, String prehash) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return java.util.Base64.getEncoder()
          .encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Failed to sign UTA WS trading request", e);
    }
  }

  /**
   * Places an order over the trading socket.
   *
   * @return the provider result
   * @throws UtaApiException with {@link RetryClassification#UNKNOWN_OUTCOME} when the socket
   *     dropped before a definitive response — the caller must reconcile, never resubmit
   */
  public UtaOrderResult placeOrder(ObjectNode args) throws IOException {
    return request("uta.order", args);
  }

  /** Cancels an order over the trading socket; same unknown-outcome semantics as placement. */
  public UtaOrderResult cancelOrder(ObjectNode args) throws IOException {
    return request("uta.cancel", args);
  }

  private UtaOrderResult request(String op, ObjectNode args) throws IOException {
    String id = Long.toString(refCount.incrementAndGet());
    ObjectNode request = objectMapper.createObjectNode();
    request.put("id", id);
    request.put("op", op);
    request.set("args", args);

    CompletableFuture<JsonNode> future = new CompletableFuture<>();
    pending.put(id, future);
    sendMessage(objectMapper.writeValueAsString(request));

    try {
      JsonNode response = future.get(30, TimeUnit.SECONDS);
      JsonNode data = response.path("data");
      UtaOrderResult result = new UtaOrderResult();
      result.setOrderId(data.path("orderId").asText(null));
      result.setClientOid(data.path("clientOid").asText(null));
      result.setTradeType(data.path("tradeType").asText(null));
      result.setTs(data.path("ts").isNumber() ? data.path("ts").asLong() : null);
      return result;
    } catch (java.util.concurrent.TimeoutException e) {
      pending.remove(id);
      throw new UtaApiException(
          "UTA WS trading request timed out",
          e,
          org.knowm.xchange.kucoin.KucoinApiMode.UTA,
          "trade",
          "wss://wsapi.kucoin.com/v1/private",
          RetryClassification.UNKNOWN_OUTCOME);
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof UtaApiException) {
        throw (UtaApiException) cause;
      }
      throw new UtaApiException(
          cause == null ? "UTA WS trading failure" : cause.getMessage(),
          cause,
          org.knowm.xchange.kucoin.KucoinApiMode.UTA,
          "trade",
          "wss://wsapi.kucoin.com/v1/private",
          RetryClassification.UNKNOWN_OUTCOME);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UtaApiException(
          "Interrupted while waiting for UTA WS trading response",
          e,
          org.knowm.xchange.kucoin.KucoinApiMode.UTA,
          "trade",
          "wss://wsapi.kucoin.com/v1/private",
          RetryClassification.UNKNOWN_OUTCOME);
    }
  }

  private static UtaApiException unknownOutcome(String message, Throwable cause) {
    return new UtaApiException(
        message,
        cause,
        org.knowm.xchange.kucoin.KucoinApiMode.UTA,
        "trade",
        "wss://wsapi.kucoin.com/v1/private",
        RetryClassification.UNKNOWN_OUTCOME);
  }

  @Override
  protected void handleMessage(JsonNode message) {
    String op = message.path("op").asText(null);
    if ("pong".equals(op)) {
      return;
    }
    if (message.has("sessionId") && message.has("timestamp") && !message.has("data")) {
      authenticate(message);
      return;
    }
    if ("welcome".equals(message.path("data").asText(null))) {
      onWelcome(message);
      return;
    }
    String id = message.path("id").asText(null);
    if (id != null && pending.containsKey(id)) {
      CompletableFuture<JsonNode> future = pending.remove(id);
      String code = message.path("code").asText("200000");
      if ("200000".equals(code)) {
        future.complete(message);
      } else {
        future.completeExceptionally(
            new UtaApiException(
                message.path("msg").asText("UTA WS trading error"),
                code,
                org.knowm.xchange.kucoin.KucoinApiMode.UTA,
                "trade",
                "wss://wsapi.kucoin.com/v1/private",
                null,
                null,
                null,
                RetryClassification.NON_RETRYABLE));
      }
      return;
    }
    LOG.debug("Unhandled UTA WS trading message: {}", message);
  }

  private void authenticate(JsonNode challenge) {
    String sessionId = challenge.path("sessionId").asText();
    long timestamp = challenge.path("timestamp").asLong();
    String prehash = "{\"sessionId\":\"" + sessionId + "\",\"timestamp\":" + timestamp + "}";
    String sign = hmacSha256Base64(secretKey, prehash);
    ObjectNode auth = objectMapper.createObjectNode();
    auth.put("sessionId", sessionId);
    auth.put("timestamp", timestamp);
    auth.put("sign", sign);
    sendMessage(auth.toString());
  }

  private void onWelcome(JsonNode welcome) {
    authenticated = true;
    int pingInterval = welcome.path("pingInterval").asInt(18000);
    if (pingSubscription != null && !pingSubscription.isDisposed()) {
      pingSubscription.dispose();
    }
    pingSubscription =
        Observable.interval(pingInterval, pingInterval, TimeUnit.MILLISECONDS)
            .subscribe(
                tick -> {
                  if (isSocketOpen()) {
                    sendMessage(
                        "{\"id\":\"ping-"
                            + refCount.incrementAndGet()
                            + "\",\"op\":\"ping\",\"timestamp\":"
                            + System.currentTimeMillis()
                            + "}");
                  }
                },
                e -> LOG.warn("UTA WS trading ping loop failed", e));
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  @Override
  protected void handleChannelMessage(String channel, JsonNode message) {
    // Not channel based.
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    return null;
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) {
    throw new UnsupportedOperationException("UTA WS trading socket is not subscription based");
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) {
    throw new UnsupportedOperationException("UTA WS trading socket is not subscription based");
  }

  @Override
  protected Completable openConnection() {
    return super.openConnection().doOnComplete(() -> authenticated = false);
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshaker, WebSocketClientHandler.WebSocketMessageHandler handler) {
    return new TradingWebSocketClientHandler(handshaker, handler);
  }

  private class TradingWebSocketClientHandler extends NettyWebSocketClientHandler {
    public TradingWebSocketClientHandler(
        WebSocketClientHandshaker handshaker, WebSocketMessageHandler handler) {
      super(handshaker, handler);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      authenticated = false;
      if (pingSubscription != null && !pingSubscription.isDisposed()) {
        pingSubscription.dispose();
      }
      // Fail pending placements explicitly; they must never be silently resent.
      for (Map.Entry<String, CompletableFuture<JsonNode>> entry : pending.entrySet()) {
        entry.getValue().completeExceptionally(
            unknownOutcome("UTA WS trading socket disconnected before a definitive response", null));
      }
      pending.clear();
      super.channelInactive(ctx);
    }
  }
}
