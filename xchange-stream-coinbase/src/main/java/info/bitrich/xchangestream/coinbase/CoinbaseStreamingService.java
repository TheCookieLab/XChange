package info.bitrich.xchangestream.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientCompressionAllowClientNoContextAndServerNoContextHandler;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coinbase Advanced Trade streaming service responsible for low-level websocket management,
 * including subscription tracking, throttling and JWT refresh for private channels.
 */
public class CoinbaseStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(CoinbaseStreamingService.class);

  private static final long DEFAULT_JWT_REFRESH_PERIOD_SECONDS =
      Math.max(CoinbaseV3Digest.JWT_EXPIRY_SECONDS - 30, 30);
  private static final String SUBSCRIBE = "subscribe";
  private static final String UNSUBSCRIBE = "unsubscribe";

  private final Supplier<String> jwtSupplier;
  private final CoinbaseRateLimiter publicRateLimiter;
  private final CoinbaseRateLimiter privateRateLimiter;
  private final long jwtRefreshPeriodSeconds;
  private ScheduledExecutorService jwtRefreshScheduler;

  private final Map<CoinbaseChannel, ChannelState> channelStates = new ConcurrentHashMap<>();

  private static ScheduledExecutorService createDefaultJwtScheduler() {
    return Executors.newSingleThreadScheduledExecutor(
        r -> {
          Thread t = new Thread(r, "coinbase-ws-jwt-refresh");
          t.setDaemon(true);
          return t;
        });
  }

  CoinbaseStreamingService(
      String apiUrl,
      Supplier<String> jwtSupplier,
      Duration connectionTimeout,
      Duration retryDuration,
      int idleTimeoutSeconds,
      int maxFramePayloadLength,
      int unauthenticatedPerSecond,
      int authenticatedPerSecond) {
    this(
        apiUrl,
        jwtSupplier,
        connectionTimeout,
        retryDuration,
        idleTimeoutSeconds,
        maxFramePayloadLength,
        unauthenticatedPerSecond,
        authenticatedPerSecond,
        DEFAULT_JWT_REFRESH_PERIOD_SECONDS,
        createDefaultJwtScheduler());
  }

  CoinbaseStreamingService(
      String apiUrl,
      Supplier<String> jwtSupplier,
      Duration connectionTimeout,
      Duration retryDuration,
      int idleTimeoutSeconds,
      int maxFramePayloadLength,
      int unauthenticatedPerSecond,
      int authenticatedPerSecond,
      long jwtRefreshPeriodSeconds,
      ScheduledExecutorService jwtRefreshScheduler) {
    super(apiUrl, maxFramePayloadLength, connectionTimeout, retryDuration, idleTimeoutSeconds);
    this.jwtSupplier = jwtSupplier == null ? () -> null : jwtSupplier;
    this.publicRateLimiter = new CoinbaseRateLimiter(unauthenticatedPerSecond);
    this.privateRateLimiter = new CoinbaseRateLimiter(authenticatedPerSecond);
    this.jwtRefreshPeriodSeconds = Math.max(1, jwtRefreshPeriodSeconds);
    this.jwtRefreshScheduler =
        jwtRefreshScheduler != null ? jwtRefreshScheduler : createDefaultJwtScheduler();
  }

  CoinbaseStreamingService(
      String apiUrl, Supplier<String> jwtSupplier, int unauthenticatedPerSecond, int authenticatedPerSecond) {
    // Use 100MB max frame payload length as recommended by Coinbase for large order book snapshots
    // (especially for high-volume products like BTC-USD)
    // See: https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/websocket/websocket-overview
    // and: https://github.com/ccxt/ccxt/issues/23597
    this(
        apiUrl,
        jwtSupplier,
        DEFAULT_CONNECTION_TIMEOUT,
        DEFAULT_RETRY_DURATION,
        45,
        Integer.MAX_VALUE,
        unauthenticatedPerSecond,
        authenticatedPerSecond);
  }

  @Override
  public void messageHandler(String message) {
    LOG.info("Coinbase WebSocket received message (length={} bytes): {}", message.length(), 
        message.length() > 200 ? message.substring(0, 200) + "..." : message);
    if (message.length() > 10 * 1024 * 1024) { // Log warning for messages > 10MB
      LOG.warn("Received very large message ({} MB) - this may indicate a large order book snapshot", 
          message.length() / (1024 * 1024));
    }
    super.messageHandler(message);
  }

  Observable<JsonNode> subscribeChannel(CoinbaseSubscriptionRequest request) {
    CoinbaseChannel channel = request.getChannel();
    ChannelState state = channelStates.computeIfAbsent(channel, ChannelState::new);
    state.incrementRefCounts(request.getProductIds(), request.getChannelArgs());

    ensureChannelStream(state);
    // Ensure the remote endpoint is aware of the requested products.
    // According to Coinbase docs, a subscribe message MUST be sent within 5 seconds
    // or the connection will be closed. Always send subscribe if we have active subscriptions.
    synchronized (state) {
      List<String> newlySubscribed = state.flushPendingSubscribes();
      List<String> productsToSubscribe = newlySubscribed.isEmpty() ? state.currentProducts() : newlySubscribed;
      LOG.debug("subscribeChannel for {}: newlySubscribed={}, productsToSubscribe={}, requiresAuth={}", 
          state.channel.channelName(), newlySubscribed, productsToSubscribe, state.requiresAuthentication());
      // Send subscribe message if:
      // 1. There are newly subscribed products, OR
      // 2. Channel requires authentication (needs JWT), OR
      // 3. We have products to subscribe to (ensures subscribe is sent even if products were already subscribed)
      if (!newlySubscribed.isEmpty() || state.requiresAuthentication() || !productsToSubscribe.isEmpty()) {
        LOG.info("Sending subscribe message for channel {} with products: {}", state.channel.channelName(), productsToSubscribe);
        sendChannelCommand(state, SUBSCRIBE, productsToSubscribe);
      } else {
        LOG.warn("NOT sending subscribe message for channel {} - no products and no auth required", state.channel.channelName());
      }
    }

    scheduleJwtRefreshIfActive(state);
    return state.stream;
  }

  void unsubscribeChannel(CoinbaseSubscriptionRequest request) {
    CoinbaseChannel channel = request.getChannel();
    ChannelState state = channelStates.get(channel);
    if (state == null) {
      return;
    }

    ChannelState.DecrementResult result = state.decrementRefCounts(request.getProductIds());
    List<String> productsToRemove = result.getProductsToRemove();
    if (!productsToRemove.isEmpty()) {
      sendChannelCommand(state, UNSUBSCRIBE, productsToRemove);
    }
    if (state.requiresAuthentication() && !result.hasActiveSubscriptions()) {
      state.cancelJwtRefresh();
    }
  }

  /**
   * Observes a channel with automatic subscription management. Protected for testing.
   *
   * @param request The subscription request
   * @return Observable stream of messages from the channel
   */
  protected Observable<JsonNode> observeChannel(CoinbaseSubscriptionRequest request) {
    return Observable.using(
        () -> request,
        this::subscribeChannel,
        this::unsubscribeChannel);
  }

  private void ensureChannelStream(ChannelState state) {
    if (state.stream != null) {
      return;
    }
    synchronized (state) {
      if (state.stream != null) {
        return;
      }
      Observable<JsonNode> upstream = super.subscribeChannel(state.channel.channelName(), state);
      state.stream = upstream.share();
    }
  }

  private void scheduleJwtRefreshIfActive(ChannelState state) {
    if (!state.requiresAuthentication()) {
      return;
    }
    try {
      state.scheduleJwtRefresh(
          jwtRefreshScheduler, jwtRefreshPeriodSeconds, () -> refreshJwt(state));
    } catch (RejectedExecutionException e) {
      LOG.debug(
          "Skipping JWT refresh scheduling for channel {} because scheduler rejected task",
          state.channel.channelName(),
          e);
    }
  }

  private void refreshJwt(ChannelState state) {
    if (!state.requiresAuthentication()) {
      return;
    }
    if (!state.hasActiveSubscriptions()) {
      state.cancelJwtRefresh();
      return;
    }
    List<String> products = state.currentProducts();
    LOG.debug("Refreshing Coinbase WebSocket JWT for channel {} with {} products",
        state.channel.channelName(), products.size());
    sendChannelCommand(state, SUBSCRIBE, products);
  }

  @Override
  public Completable disconnect() {
    return super.disconnect()
        .doFinally(
            () -> {
              channelStates.values().forEach(state -> state.cancelJwtRefresh());
              channelStates.clear();
              // Don't shut down the scheduler - the service instance may be reused on reconnect.
              // Canceling the JWT refresh tasks above is sufficient to clean up scheduled work.
            });
  }

  private void sendChannelCommand(ChannelState state, String type, List<String> productIds) {
    if (type.equals(SUBSCRIBE) && state.requiresAuthentication() && productIds.isEmpty()) {
      // still refresh auth to keep connection alive
      productIds = state.currentProducts();
    }
    List<String> sanitized = state.sanitizeProductIds(productIds);
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", type);
    payload.put("channel", state.channel.channelName());
    payload.put("product_ids", sanitized);
    if (!state.args.isEmpty()) {
      payload.putAll(state.args);
    }

    // Include JWT if channel requires authentication OR if JWT supplier is available
    // (some channels may work better with JWT even if not strictly required)
    if (state.requiresAuthentication() || jwtSupplier != null) {
      String jwt = jwtSupplier.get();
      if (jwt != null && !jwt.isEmpty()) {
        payload.put("jwt", jwt);
        if (!state.requiresAuthentication()) {
          LOG.debug("Including JWT for public channel {} (may be required by Coinbase)", state.channel.channelName());
        }
      } else if (state.requiresAuthentication()) {
        LOG.warn(
            "Coinbase Advanced Trade websocket channel {} requires authentication but no JWT was available",
            state.channel.channelName());
        return;
      }
    }

    try {
      CoinbaseRateLimiter limiter =
          state.requiresAuthentication() ? privateRateLimiter : publicRateLimiter;
      limiter.acquire();
      String payloadJson;
      try {
        payloadJson = objectMapper.writeValueAsString(payload);
      } catch (Exception e) {
        payloadJson = payload.toString();
      }
      LOG.info("Sending Coinbase WebSocket {} message for channel {} with products: {} (hasJWT: {})", 
          type, state.channel.channelName(), sanitized, payload.containsKey("jwt"));
      LOG.info("Full subscribe payload: {}", payloadJson);
      sendObjectMessage(payload);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while throttling Coinbase websocket command", e);
    }
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    ChannelState state = (ChannelState) args[0];
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", SUBSCRIBE);
    payload.put("channel", state.channel.channelName());
    payload.put("product_ids", state.currentProducts());
    if (!state.args.isEmpty()) {
      payload.putAll(state.args);
    }
    if (state.requiresAuthentication()) {
      String jwt = jwtSupplier.get();
      if (jwt == null || jwt.isEmpty()) {
        throw new IOException(
            "JWT token required for channel " + state.channel.channelName() + " but none provided");
      }
      payload.put("jwt", jwt);
    }
    return objectMapper.writeValueAsString(payload);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    ChannelState state = (ChannelState) args[0];
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", UNSUBSCRIBE);
    payload.put("channel", state.channel.channelName());
    payload.put("product_ids", Collections.emptyList());
    if (state.requiresAuthentication()) {
      String jwt = jwtSupplier.get();
      if (jwt != null && !jwt.isEmpty()) {
        payload.put("jwt", jwt);
      }
    }
    return objectMapper.writeValueAsString(payload);
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    return channelName;
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    JsonNode channelNode = message.get("channel");
    if (channelNode == null || channelNode.isNull()) {
      LOG.debug("Message has no 'channel' field: {}", message);
      return null;
    }
    String channelName = channelNode.asText();
    LOG.debug("Extracted channel name from message: {}", channelName);
    
    // Coinbase may send "l2_data" even when we subscribe to "level2" or "level2_batch"
    // Check if we have a subscription for "level2" or "level2_batch" and map "l2_data" accordingly
    if ("l2_data".equals(channelName)) {
      // Try to find an existing subscription - prefer "level2_batch" if it exists, otherwise "level2"
      // The channels map is protected in NettyStreamingService, so we can access it
      String normalizedChannel = null;
      if (channels.containsKey("level2_batch")) {
        normalizedChannel = "level2_batch";
        LOG.info("Normalizing channel name from 'l2_data' to 'level2_batch' (subscription exists)");
      } else if (channels.containsKey("level2")) {
        normalizedChannel = "level2";
        LOG.info("Normalizing channel name from 'l2_data' to 'level2' (subscription exists)");
      } else {
        // Default to "level2" if no subscription found yet (shouldn't happen, but be safe)
        normalizedChannel = "level2";
        LOG.warn("Normalizing channel name from 'l2_data' to 'level2' (default - no subscription found)");
      }
      channelName = normalizedChannel;
    }
    
    return channelName;
  }

  @Override
  protected WebSocketClientExtensionHandler getWebSocketClientExtensionHandler() {
    return WebSocketClientCompressionAllowClientNoContextAndServerNoContextHandler.INSTANCE;
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshaker,
      WebSocketClientHandler.WebSocketMessageHandler handler) {
    return new CoinbaseWebSocketClientHandler(handshaker, handler);
  }

  private class CoinbaseWebSocketClientHandler extends NettyWebSocketClientHandler {
    CoinbaseWebSocketClientHandler(
        WebSocketClientHandshaker handshaker, WebSocketMessageHandler handler) {
      super(handshaker, handler);
    }

    @Override
    public void channelInactive(io.netty.channel.ChannelHandlerContext ctx) {
      super.channelInactive(ctx);
      channelStates.values().forEach(ChannelState::resetPending);
    }
  }

  private static class CoinbaseRateLimiter {
    private final int maxPermitsPerSecond;
    private long windowStartNanos;
    private int permitsIssued;

    CoinbaseRateLimiter(int maxPermitsPerSecond) {
      this.maxPermitsPerSecond = Math.max(1, maxPermitsPerSecond);
      this.windowStartNanos = System.nanoTime();
      this.permitsIssued = 0;
    }

    synchronized void acquire() throws InterruptedException {
      long now = System.nanoTime();
      long windowElapsed = now - windowStartNanos;
      if (windowElapsed >= TimeUnit.SECONDS.toNanos(1)) {
        windowStartNanos = now;
        permitsIssued = 0;
      }
      if (permitsIssued >= maxPermitsPerSecond) {
        long waitNanos = TimeUnit.SECONDS.toNanos(1) - windowElapsed;
        if (waitNanos > 0) {
          TimeUnit.NANOSECONDS.sleep(waitNanos);
        }
        windowStartNanos = System.nanoTime();
        permitsIssued = 0;
      }
      permitsIssued++;
    }
  }

  private static final class ChannelState {
    private static final String NO_PRODUCT_KEY = "*";

    private final CoinbaseChannel channel;
    private final Map<String, AtomicInteger> productRefCounts = new HashMap<>();
    private final Set<String> pendingSubscribe = new HashSet<>();
    private final Map<String, Object> args = new HashMap<>();
    private Observable<JsonNode> stream;
    private boolean usesExplicitProducts = true;
    private ScheduledFuture<?> jwtRefreshFuture;

    private ChannelState(CoinbaseChannel channel) {
      this.channel = channel;
    }

    synchronized void incrementRefCounts(List<String> products, Map<String, Object> channelArgs) {
      boolean requestHasProducts = products != null && !products.isEmpty();
      if (!requestHasProducts) {
        usesExplicitProducts = false;
        products = Collections.singletonList(NO_PRODUCT_KEY);
      }
      for (String product : products) {
        AtomicInteger counter = productRefCounts.computeIfAbsent(product, p -> new AtomicInteger());
        if (counter.getAndIncrement() == 0) {
          pendingSubscribe.add(product);
        }
      }
      if (channelArgs != null && !channelArgs.isEmpty()) {
        args.putAll(channelArgs);
      }
    }

    synchronized DecrementResult decrementRefCounts(List<String> products) {
      boolean requestHasProducts = products != null && !products.isEmpty();
      if (!requestHasProducts) {
        products = Collections.singletonList(NO_PRODUCT_KEY);
      }
      List<String> toRemove = new ArrayList<>();
      for (String product : products) {
        AtomicInteger counter = productRefCounts.get(product);
        if (counter == null) {
          continue;
        }
        int remaining = counter.decrementAndGet();
        if (remaining <= 0) {
          productRefCounts.remove(product);
          toRemove.add(product);
        }
      }
      return new DecrementResult(toRemove, !productRefCounts.isEmpty());
    }

    synchronized List<String> flushPendingSubscribes() {
      if (pendingSubscribe.isEmpty()) {
        return Collections.emptyList();
      }
      List<String> pending = new ArrayList<>(pendingSubscribe);
      pendingSubscribe.clear();
      if (!usesExplicitProducts) {
        return Collections.singletonList(NO_PRODUCT_KEY);
      }
      pending.remove(NO_PRODUCT_KEY);
      return pending;
    }

    synchronized List<String> currentProducts() {
      if (!usesExplicitProducts) {
        return Collections.emptyList();
      }
      List<String> result = new ArrayList<>(productRefCounts.keySet());
      result.remove(NO_PRODUCT_KEY);
      return result;
    }

    synchronized boolean hasActiveSubscriptions() {
      return !productRefCounts.isEmpty();
    }

    synchronized void scheduleJwtRefresh(
        ScheduledExecutorService scheduler, long periodSeconds, Runnable refreshTask) {
      if (!requiresAuthentication()) {
        return;
      }
      if (!hasActiveSubscriptions()) {
        return;
      }
      if (jwtRefreshFuture != null && !jwtRefreshFuture.isCancelled() && !jwtRefreshFuture.isDone()) {
        return;
      }
      jwtRefreshFuture =
          scheduler.scheduleAtFixedRate(refreshTask, periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    synchronized void cancelJwtRefresh() {
      if (jwtRefreshFuture != null) {
        jwtRefreshFuture.cancel(false);
        jwtRefreshFuture = null;
      }
    }

    boolean requiresAuthentication() {
      return channel.requiresAuthentication();
    }

    void resetPending() {
      synchronized (this) {
        pendingSubscribe.addAll(productRefCounts.keySet());
      }
    }

    List<String> sanitizeProductIds(List<String> rawProducts) {
      if (rawProducts == null || rawProducts.isEmpty()) {
        return Collections.emptyList();
      }
      if (!usesExplicitProducts) {
        return Collections.emptyList();
      }
      List<String> sanitized = new ArrayList<>();
      for (String rawProduct : rawProducts) {
        if (!NO_PRODUCT_KEY.equals(rawProduct)) {
          sanitized.add(rawProduct);
        }
      }
      return sanitized;
    }

    private static final class DecrementResult {
      private final List<String> productsToRemove;
      private final boolean hasActiveSubscriptions;

      private DecrementResult(List<String> productsToRemove, boolean hasActiveSubscriptions) {
        this.productsToRemove = productsToRemove;
        this.hasActiveSubscriptions = hasActiveSubscriptions;
      }

      List<String> getProductsToRemove() {
        return productsToRemove;
      }

      boolean hasActiveSubscriptions() {
        return hasActiveSubscriptions;
      }
    }
  }
}
