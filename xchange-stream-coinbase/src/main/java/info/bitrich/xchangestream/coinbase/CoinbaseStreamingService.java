package info.bitrich.xchangestream.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientCompressionAllowClientNoContextAndServerNoContextHandler;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
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
import java.util.concurrent.ScheduledExecutorService;
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
class CoinbaseStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(CoinbaseStreamingService.class);

  private static final String SUBSCRIBE = "subscribe";
  private static final String UNSUBSCRIBE = "unsubscribe";

  private final Supplier<String> jwtSupplier;
  private final CoinbaseRateLimiter publicRateLimiter;
  private final CoinbaseRateLimiter privateRateLimiter;
  private final ScheduledExecutorService jwtRefreshScheduler;

  private final Map<CoinbaseChannel, ChannelState> channelStates = new ConcurrentHashMap<>();

  CoinbaseStreamingService(
      String apiUrl,
      Supplier<String> jwtSupplier,
      Duration connectionTimeout,
      Duration retryDuration,
      int idleTimeoutSeconds,
      int maxFramePayloadLength,
      int unauthenticatedPerSecond,
      int authenticatedPerSecond) {
    super(apiUrl, maxFramePayloadLength, connectionTimeout, retryDuration, idleTimeoutSeconds);
    this.jwtSupplier = jwtSupplier == null ? () -> null : jwtSupplier;
    this.publicRateLimiter = new CoinbaseRateLimiter(unauthenticatedPerSecond);
    this.privateRateLimiter = new CoinbaseRateLimiter(authenticatedPerSecond);
    this.jwtRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "coinbase-ws-jwt-refresh");
      t.setDaemon(true);
      return t;
    });
  }

  CoinbaseStreamingService(
      String apiUrl, Supplier<String> jwtSupplier, int unauthenticatedPerSecond, int authenticatedPerSecond) {
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

  Observable<JsonNode> subscribeChannel(CoinbaseSubscriptionRequest request) {
    CoinbaseChannel channel = request.getChannel();
    ChannelState state = channelStates.computeIfAbsent(channel, ChannelState::new);
    state.incrementRefCounts(request.getProductIds(), request.getChannelArgs());

    ensureChannelStream(state);
    // Ensure the remote endpoint is aware of the requested products.
    synchronized (state) {
      List<String> newlySubscribed = state.flushPendingSubscribes();
      if (!newlySubscribed.isEmpty() || state.requiresAuthentication()) {
        sendChannelCommand(state, SUBSCRIBE, newlySubscribed.isEmpty() ? state.currentProducts() : newlySubscribed);
      }
    }

    return state.stream;
  }

  void unsubscribeChannel(CoinbaseSubscriptionRequest request) {
    CoinbaseChannel channel = request.getChannel();
    ChannelState state = channelStates.get(channel);
    if (state == null) {
      return;
    }

    List<String> productsToRemove = state.decrementRefCounts(request.getProductIds());
    if (!productsToRemove.isEmpty()) {
      sendChannelCommand(state, UNSUBSCRIBE, productsToRemove);
    }
  }

  Observable<JsonNode> observeChannel(CoinbaseSubscriptionRequest request) {
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
      if (state.requiresAuthentication()) {
        long refreshPeriod = Math.max(CoinbaseV3Digest.JWT_EXPIRY_SECONDS - 30, 30);
        jwtRefreshScheduler.scheduleAtFixedRate(
            () -> refreshJwt(state),
            refreshPeriod,
            refreshPeriod,
            TimeUnit.SECONDS);
      }
    }
  }

  private void refreshJwt(ChannelState state) {
    if (!state.requiresAuthentication()) {
      return;
    }
    List<String> products = state.currentProducts();
    if (products.isEmpty()) {
      return;
    }
    LOG.debug("Refreshing Coinbase WebSocket JWT for channel {} with {} products",
        state.channel.channelName(), products.size());
    sendChannelCommand(state, SUBSCRIBE, products);
  }

  @Override
  public Completable disconnect() {
    return super.disconnect()
        .doFinally(
            () -> {
              channelStates.clear();
              jwtRefreshScheduler.shutdownNow();
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

    if (state.requiresAuthentication()) {
      String jwt = jwtSupplier.get();
      if (jwt == null || jwt.isEmpty()) {
        LOG.warn(
            "Coinbase Advanced Trade websocket channel {} requires authentication but no JWT was available",
            state.channel.channelName());
        return;
      }
      payload.put("jwt", jwt);
    }

    try {
      CoinbaseRateLimiter limiter =
          state.requiresAuthentication() ? privateRateLimiter : publicRateLimiter;
      limiter.acquire();
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
      return null;
    }
    return channelNode.asText();
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

    synchronized List<String> decrementRefCounts(List<String> products) {
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
      return toRemove;
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
  }
}
