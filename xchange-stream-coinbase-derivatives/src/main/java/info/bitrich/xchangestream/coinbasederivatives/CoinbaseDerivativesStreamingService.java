package info.bitrich.xchangestream.coinbasederivatives;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.knowm.xchange.coinbasederivatives.client.CoinbaseDerivativesRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coinbase-owned JSON-RPC 2.0 WebSocket transport on XChange's Netty streaming core. */
public class CoinbaseDerivativesStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG =
      LoggerFactory.getLogger(CoinbaseDerivativesStreamingService.class);
  private static final int MAX_DEDUPLICATION_KEYS = 10_000;

  private final CoinbaseDerivativesStreamConfiguration configuration;
  private final AtomicLong connectionGeneration = new AtomicLong();
  private final AtomicLong requestSequence = new AtomicLong();
  private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
  private final Map<String, Long> controlRequests = new ConcurrentHashMap<>();
  private final Map<String, Observable<JsonNode>> channelObservables = new ConcurrentHashMap<>();
  private final Map<String, Long> channelChangeIds = new ConcurrentHashMap<>();
  private final Map<String, Boolean> privateChannels = new ConcurrentHashMap<>();
  private final Map<String, Boolean> seenEvents =
      java.util.Collections.synchronizedMap(
          new LinkedHashMap<String, Boolean>(MAX_DEDUPLICATION_KEYS + 1, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
              return size() > MAX_DEDUPLICATION_KEYS;
            }
          });
  private final Subject<Throwable> protocolErrors =
      PublishSubject.<Throwable>create().toSerialized();
  private final ScheduledExecutorService lifecycleScheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "coinbase-derivatives-ws-lifecycle");
            thread.setDaemon(true);
            return thread;
          });
  private final CreditAwareBackoff creditBackoff = new CreditAwareBackoff();

  private volatile boolean authenticated;
  private volatile Optional<ScheduledFuture<?>> reauthenticationTask = Optional.empty();
  private volatile long creditBlockedUntilNanos;

  public CoinbaseDerivativesStreamingService(
      String apiUrl, CoinbaseDerivativesStreamConfiguration configuration) {
    super(apiUrl, Integer.MAX_VALUE, Duration.ofSeconds(10), Duration.ofSeconds(15), 15);
    this.configuration = Objects.requireNonNull(configuration);
  }

  /** Returns protocol and sequence errors that cannot be associated with a channel subscriber. */
  public Observable<Throwable> protocolErrors() {
    return protocolErrors.hide();
  }

  /** Returns the monotonically increasing local connection-generation identifier. */
  public long getConnectionGeneration() {
    return connectionGeneration.get();
  }

  /** Returns whether the current WebSocket generation completed {@code public/auth}. */
  public boolean isAuthenticated() {
    return authenticated;
  }

  /** Subscribe to a public provider channel. */
  public Observable<JsonNode> subscribePublicChannel(String channel) {
    return subscribeProviderChannel(channel, false);
  }

  /** Subscribe to a private provider channel, queued until session authentication completes. */
  public Observable<JsonNode> subscribePrivateChannel(String channel) {
    if (configuration.jwtSupplier() == null) {
      return Observable.error(
          new org.knowm.xchange.exceptions.ExchangeSecurityException(
              "Coinbase CDP credentials are required for private streaming channels"));
    }
    return subscribeProviderChannel(channel, true);
  }

  private Observable<JsonNode> subscribeProviderChannel(String channel, boolean privateChannel) {
    privateChannels.merge(channel, privateChannel, Boolean::logicalOr);
    return channelObservables.computeIfAbsent(
        channel,
        ignored ->
            privateChannel
                ? queuedPrivateSubscription(channel)
                : super.subscribeChannel(channel, false)
                    .doFinally(
                        () -> {
                          channelObservables.remove(channel);
                          privateChannels.remove(channel);
                        }));
  }

  private Observable<JsonNode> queuedPrivateSubscription(String channel) {
    return Observable.<JsonNode>create(
            emitter -> {
              if (!isSocketOpen()) {
                emitter.onError(
                    new info.bitrich.xchangestream.service.exception.NotConnectedException());
                return;
              }
              channels.computeIfAbsent(
                  channel,
                  ignored -> {
                    Subscription subscription =
                        new Subscription(emitter, channel, new Object[] {Boolean.TRUE});
                    if (authenticated) {
                      try {
                        sendMessage(getSubscribeMessage(channel, Boolean.TRUE));
                      } catch (IOException failure) {
                        emitter.onError(failure);
                      }
                    }
                    return subscription;
                  });
            })
        .doOnDispose(
            () -> {
              if (channels.remove(channel) != null && authenticated) {
                sendMessage(getUnsubscribeMessage(channel, Boolean.TRUE));
              }
              channelObservables.remove(channel);
              privateChannels.remove(channel);
            })
        .share();
  }

  /**
   * Sends a correlated JSON-RPC request.
   *
   * <p>The returned response belongs to the current connection generation. Ambiguous private writes
   * should pass {@code replaySafe=false}; such requests are failed on disconnect and are never
   * automatically replayed.
   */
  public Single<JsonNode> request(String method, JsonNode params, boolean replaySafe) {
    long delayMillis = remainingCreditDelayMillis();
    if (delayMillis > 0) {
      return Single.timer(delayMillis, TimeUnit.MILLISECONDS)
          .flatMap(ignored -> request(method, params, replaySafe));
    }
    return requestNow(method, params, replaySafe);
  }

  private Single<JsonNode> requestNow(String method, JsonNode params, boolean replaySafe) {
    return Single.create(
        emitter -> {
          long generation = connectionGeneration.get();
          if (generation == 0 || !isSocketOpen()) {
            emitter.onError(new CoinbaseDerivativesStreamException("WebSocket is not connected"));
            return;
          }
          long numericId = requestSequence.incrementAndGet();
          String id = Long.toString(numericId);
          PendingRequest pending = new PendingRequest(generation, method, replaySafe, emitter);
          pendingRequests.put(id, pending);
          emitter.setCancellable(() -> pendingRequests.remove(id, pending));

          ObjectNode request = objectMapper.createObjectNode();
          request.put("jsonrpc", "2.0");
          request.put("id", numericId);
          request.put("method", method);
          request.set("params", params == null ? objectMapper.createObjectNode() : params);
          sendObjectMessage(request);
        });
  }

  /** Starts a fresh authentication exchange using a newly generated CDP JWT. */
  public Completable reauthenticate() {
    if (configuration.jwtSupplier() == null) {
      return Completable.complete();
    }
    return Completable.defer(
        () -> {
          String jwt = configuration.jwtSupplier().get();
          if (jwt == null || jwt.isBlank()) {
            return Completable.error(
                new CoinbaseDerivativesStreamException(
                    "Coinbase CDP JWT supplier returned no token"));
          }

          ObjectNode params = objectMapper.createObjectNode();
          params.put("grant_type", "coinbase_cdp");
          params.put("token", jwt);
          return request("public/auth", params, true)
              .doOnSuccess(
                  ignored -> {
                    authenticated = true;
                    scheduleReauthentication();
                    configureCancelOnDisconnect();
                    resubscribeRegisteredChannels(true);
                  })
              .doOnError(ignored -> authenticated = false)
              .ignoreElement();
        });
  }

  @Override
  public void resubscribeChannels() {
    beginConnectionGeneration();
    long delayMillis = remainingCreditDelayMillis();
    if (delayMillis > 0) {
      LOG.warn(
          "Deferring Coinbase derivatives resubscription for {} ms after credit exhaustion",
          delayMillis);
      lifecycleScheduler.schedule(this::startSession, delayMillis, TimeUnit.MILLISECONDS);
    } else {
      startSession();
    }
  }

  private void startSession() {
    configureHeartbeat();
    resubscribeRegisteredChannels(false);
    reauthenticate()
        .subscribe(
            () -> LOG.debug("Coinbase derivatives WebSocket session authenticated"),
            failure -> {
              authenticated = false;
              protocolErrors.onNext(failure);
              LOG.warn(
                  "Coinbase derivatives WebSocket authentication failed: {}", failure.getMessage());
            });
  }

  void beginConnectionGeneration() {
    long nextGeneration = connectionGeneration.incrementAndGet();
    authenticated = false;
    channelChangeIds.clear();
    cancelReauthentication();
    List<PendingRequest> stale = new ArrayList<>(pendingRequests.values());
    pendingRequests.clear();
    controlRequests.clear();
    stale.forEach(
        pending ->
            pending.emitter.onError(
                new CoinbaseDerivativesStreamException(
                    "Connection generation changed before response for " + pending.method)));
    LOG.info(
        "Coinbase derivatives WebSocket generation {} opened; Cancel on Disconnect is {}{}",
        nextGeneration,
        configuration.isCancelOnDisconnect() ? "ENABLED" : "disabled",
        configuration.isCancelOnDisconnect()
            ? " (scope=" + configuration.getCancelOnDisconnectScope().wireValue() + ")"
            : "");
  }

  @Override
  public Completable disconnect() {
    cancelReauthentication();
    failPendingOnDisconnect();
    lifecycleScheduler.shutdownNow();
    return super.disconnect();
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    return channelName;
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    return notificationChannel(message);
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    boolean privateChannel = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
    if (privateChannel && !authenticated) {
      throw new IOException("Private subscription is queued until authentication completes");
    }
    return subscriptionMessage(
        privateChannel ? "private/subscribe" : "public/subscribe", channelName);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    boolean privateChannel = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
    return subscriptionMessage(
        privateChannel ? "private/unsubscribe" : "public/unsubscribe", channelName);
  }

  private String subscriptionMessage(String method, String channel) throws IOException {
    ObjectNode params = objectMapper.createObjectNode();
    ArrayNode channelsNode = params.putArray("channels");
    channelsNode.add(channel);
    long generation = connectionGeneration.get();
    long numericId = requestSequence.incrementAndGet();
    String id = Long.toString(numericId);
    controlRequests.put(id, generation);
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("id", numericId);
    request.put("method", method);
    request.set("params", params);
    return objectMapper.writeValueAsString(request);
  }

  @Override
  public void messageHandler(String rawMessage) {
    JsonNode message;
    try {
      message = objectMapper.readTree(rawMessage);
    } catch (IOException failure) {
      protocolErrors.onNext(
          new CoinbaseDerivativesStreamException(
              "Malformed Coinbase derivatives WebSocket JSON", failure));
      return;
    }
    handleProtocolMessage(message);
  }

  void handleProtocolMessage(JsonNode message) {
    if (!message.isObject()) {
      protocolErrors.onNext(
          new CoinbaseDerivativesStreamException("JSON-RPC WebSocket message must be an object"));
      return;
    }
    boolean creditFailure = creditBackoff.isCreditFailure(message);
    if (creditFailure) {
      creditBlockedUntilNanos =
          System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(creditBackoff.nextDelay().toMillis());
    }
    if (message.has("id") && (message.has("result") || message.has("error"))) {
      handleResponse(message, creditFailure);
      return;
    }
    String method = message.path("method").asText();
    if ("heartbeat".equals(method)
        && "test_request".equals(message.path("params").path("type").asText())) {
      request("public/test", objectMapper.createObjectNode(), true)
          .subscribe(ignored -> {}, protocolErrors::onNext);
      return;
    }
    if ("subscription".equals(method) || notificationChannel(message) != null) {
      handleNotification(message);
      return;
    }
    protocolErrors.onNext(
        new CoinbaseDerivativesStreamException(
            "Unrecognized Coinbase derivatives WebSocket message"));
  }

  private void handleResponse(JsonNode response, boolean creditFailure) {
    if (!"2.0".equals(response.path("jsonrpc").asText())
        || !response.path("id").isIntegralNumber()
        || (response.has("result") == response.has("error"))) {
      protocolErrors.onNext(
          new CoinbaseDerivativesStreamException("Malformed JSON-RPC response envelope"));
      return;
    }
    String id = response.get("id").asText();
    PendingRequest pending = pendingRequests.remove(id);
    if (pending == null) {
      Long controlGeneration = controlRequests.remove(id);
      if (controlGeneration != null && controlGeneration == connectionGeneration.get()) {
        if (response.has("error")) {
          protocolErrors.onNext(
              new CoinbaseDerivativesStreamException(
                  "Coinbase derivatives subscription request was rejected"));
        }
        return;
      }
    }
    if (pending == null || pending.generation != connectionGeneration.get()) {
      protocolErrors.onNext(
          new CoinbaseDerivativesStreamException(
              "Rejected stale or unknown JSON-RPC response id " + sanitizeId(id)));
      return;
    }
    if (response.has("error")) {
      JsonNode error = response.get("error");
      if (!error.isObject() || !error.hasNonNull("code") || !error.hasNonNull("message")) {
        pending.emitter.onError(
            new CoinbaseDerivativesStreamException(
                "Malformed Coinbase derivatives JSON-RPC error"));
        return;
      }
      String code = error.has("code") ? error.get("code").asText() : "unknown";
      String message =
          CoinbaseDerivativesRedactor.sanitize(
              error.has("message") ? error.get("message").asText() : "malformed error");
      pending.emitter.onError(
          new CoinbaseDerivativesStreamException(
              "Coinbase derivatives JSON-RPC "
                  + pending.method
                  + " failed (code="
                  + code
                  + "): "
                  + message));
      return;
    }
    if (!creditFailure) {
      creditBackoff.recovered();
      creditBlockedUntilNanos = 0;
    }
    pending.emitter.onSuccess(response.get("result"));
  }

  private void handleNotification(JsonNode message) {
    String channel = notificationChannel(message);
    if (channel == null) {
      protocolErrors.onNext(
          new CoinbaseDerivativesStreamException(
              "Subscription notification is missing its channel"));
      return;
    }
    JsonNode data = notificationData(message);
    if (data == null || data.isMissingNode()) {
      handleChannelError(
          channel,
          new CoinbaseDerivativesStreamException("Subscription notification is missing data"));
      return;
    }
    if (!validateChangeSequence(channel, data)) {
      return;
    }
    JsonNode deduplicatedData = deduplicateData(channel, data);
    if (deduplicatedData == null) {
      return;
    }
    ObjectNode normalized = objectMapper.createObjectNode();
    normalized.put("channel", channel);
    normalized.set("data", deduplicatedData);
    handleMessage(normalized);
  }

  private boolean validateChangeSequence(String channel, JsonNode data) {
    if (!data.isObject() || !data.has("change_id")) {
      return true;
    }
    long changeId = data.get("change_id").asLong();
    if ("snapshot".equals(data.path("type").asText())) {
      channelChangeIds.put(channel, changeId);
      return true;
    }
    Long previous = channelChangeIds.get(channel);
    if (previous == null || !data.path("prev_change_id").isIntegralNumber()) {
      return surfaceGap(
          channel,
          previous == null ? -1 : previous,
          data.path("prev_change_id").isIntegralNumber()
              ? data.get("prev_change_id").asLong()
              : -1);
    }
    long actualPrevious = data.get("prev_change_id").asLong();
    if (previous.longValue() != actualPrevious) {
      return surfaceGap(channel, previous, actualPrevious);
    }
    channelChangeIds.put(channel, changeId);
    return true;
  }

  private boolean surfaceGap(String channel, long expectedPrevious, long actualPrevious) {
    channelChangeIds.remove(channel);
    CoinbaseDerivativesStreamGapException gap =
        new CoinbaseDerivativesStreamGapException(channel, expectedPrevious, actualPrevious);
    handleChannelError(channel, gap);
    protocolErrors.onNext(gap);
    return false;
  }

  private JsonNode deduplicateData(String channel, JsonNode data) {
    if (data.isArray()) {
      ArrayNode filtered = objectMapper.createArrayNode();
      for (JsonNode event : data) {
        if (isNewEvent(channel, event)) {
          filtered.add(event);
        }
      }
      return filtered.isEmpty() ? null : filtered;
    }
    if (!data.isObject()) {
      return data;
    }
    ObjectNode filtered = ((ObjectNode) data).deepCopy();
    boolean hadEventCollections = false;
    boolean retainedEvent = false;
    for (String field : List.of("orders", "trades")) {
      JsonNode events = data.get(field);
      if (events == null || !events.isArray()) {
        continue;
      }
      hadEventCollections = true;
      ArrayNode retained = objectMapper.createArrayNode();
      for (JsonNode event : events) {
        if (isNewEvent(channel + ':' + field, event)) {
          retained.add(event);
          retainedEvent = true;
        }
      }
      filtered.set(field, retained);
    }
    if (hadEventCollections && !retainedEvent && !data.has("positions")) {
      return null;
    }
    return isNewEvent(channel, filtered) ? filtered : null;
  }

  private boolean isNewEvent(String channel, JsonNode event) {
    String eventKey = eventDeduplicationKey(channel, event);
    return eventKey == null || seenEvents.putIfAbsent(eventKey, Boolean.TRUE) == null;
  }

  private String eventDeduplicationKey(String channel, JsonNode data) {
    JsonNode event = data.isArray() && !data.isEmpty() ? data.get(0) : data;
    String id = firstText(event, "event_id", "trade_id", "order_id");
    if (id == null) {
      return null;
    }
    String version =
        firstText(
            event,
            "trade_seq",
            "change_id",
            "last_update_timestamp",
            "timestamp",
            "filled_amount",
            "order_state");
    return channel + ':' + id + ':' + (version == null ? "" : version);
  }

  private String firstText(JsonNode node, String... fields) {
    if (node == null || !node.isObject()) {
      return null;
    }
    for (String field : fields) {
      if (node.hasNonNull(field)) {
        return node.get(field).asText();
      }
    }
    return null;
  }

  private String notificationChannel(JsonNode message) {
    if (message.hasNonNull("channel")) {
      return message.get("channel").asText();
    }
    JsonNode params = message.get("params");
    return params != null && params.hasNonNull("channel") ? params.get("channel").asText() : null;
  }

  private JsonNode notificationData(JsonNode message) {
    if (message.has("data")) {
      return message.get("data");
    }
    JsonNode params = message.get("params");
    return params == null ? null : params.get("data");
  }

  private void configureHeartbeat() {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("interval", 10);
    request("public/set_heartbeat", params, true).subscribe(ignored -> {}, protocolErrors::onNext);
  }

  private void configureCancelOnDisconnect() {
    if (!configuration.isCancelOnDisconnect()) {
      return;
    }
    ObjectNode params = objectMapper.createObjectNode();
    params.put("scope", configuration.getCancelOnDisconnectScope().wireValue());
    request("private/enable_cancel_on_disconnect", params, true)
        .subscribe(ignored -> {}, protocolErrors::onNext);
  }

  private void resubscribeRegisteredChannels(boolean privateOnly) {
    channels
        .values()
        .forEach(
            subscription -> {
              boolean privateChannel =
                  Boolean.TRUE.equals(privateChannels.get(subscription.getChannelName()));
              if (privateOnly != privateChannel) {
                return;
              }
              try {
                sendMessage(getSubscribeMessage(subscription.getChannelName(), privateChannel));
              } catch (IOException failure) {
                handleChannelError(subscription.getChannelName(), failure);
              }
            });
  }

  private void scheduleReauthentication() {
    cancelReauthentication();
    reauthenticationTask =
        Optional.of(
            lifecycleScheduler.schedule(
                () ->
                    reauthenticate()
                        .subscribe(
                            () ->
                                LOG.debug("Coinbase derivatives WebSocket session reauthenticated"),
                            protocolErrors::onNext),
                configuration.reauthenticationDelay().toMillis(),
                TimeUnit.MILLISECONDS));
  }

  private void cancelReauthentication() {
    reauthenticationTask.ifPresent(task -> task.cancel(false));
    reauthenticationTask = Optional.empty();
  }

  private void failPendingOnDisconnect() {
    List<PendingRequest> pending = new ArrayList<>(pendingRequests.values());
    pendingRequests.clear();
    pending.forEach(
        request ->
            request.emitter.onError(
                new CoinbaseDerivativesStreamException(
                    (request.replaySafe ? "Replay-safe" : "Non-replayable")
                        + " request disconnected before response: "
                        + request.method)));
  }

  Duration currentCreditBackoff() {
    return creditBackoff.nextDelay();
  }

  long remainingCreditDelayMillis() {
    long remaining = creditBlockedUntilNanos - System.nanoTime();
    return remaining <= 0 ? 0 : Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
  }

  private String sanitizeId(String id) {
    return id != null && id.matches("[0-9]+") ? id : "<invalid>";
  }

  private static final class PendingRequest {
    private final long generation;
    private final String method;
    private final boolean replaySafe;
    private final SingleEmitter<JsonNode> emitter;

    private PendingRequest(
        long generation, String method, boolean replaySafe, SingleEmitter<JsonNode> emitter) {
      this.generation = generation;
      this.method = method;
      this.replaySafe = replaySafe;
      this.emitter = emitter;
    }
  }
}
