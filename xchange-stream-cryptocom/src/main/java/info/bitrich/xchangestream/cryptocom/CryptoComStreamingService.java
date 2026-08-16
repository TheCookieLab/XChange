package info.bitrich.xchangestream.cryptocom;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.knowm.xchange.cryptocom.CryptoComRequestIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the Crypto.com Exchange v1 WebSocket envelope shared by the public market-data feed and
 * the private user feed: {@code {"id","method","params":{"channels":[...]}}} subscribe/unsubscribe
 * requests, {@code {"id","method","code","result":{"channel","subscription","data":[...]}}} push
 * messages, and the {@code public/heartbeat} / {@code public/respond-heartbeat} keepalive that the
 * server requires every ~30 seconds or it closes the connection.
 *
 * <p>Every physical connection gets a generation id (see {@link #getConnectionGeneration()}); the
 * framework invokes {@link #resubscribeChannels()} right after each successful connection, which
 * is where the active generation is captured - so consumers can detect stale generations with
 * {@link #isCurrentConnection()}. Subscribe/unsubscribe confirmations are correlated with their
 * request ids and tracked in {@link #getActiveChannels()}. Reconnects are bounded: after {@value
 * #MAX_RECONNECT_ATTEMPTS} consecutive failed attempts the service stops reconnecting and {@link
 * #onReconnectBudgetExhausted()} is invoked.
 */
public class CryptoComStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoComStreamingService.class);
  private static final String HEARTBEAT_METHOD = "public/heartbeat";
  private static final String HEARTBEAT_RESPONSE_METHOD = "public/respond-heartbeat";
  private static final String SUBSCRIBE_METHOD = "subscribe";
  private static final String UNSUBSCRIBE_METHOD = "unsubscribe";
  static final int MAX_RECONNECT_ATTEMPTS = 10;

  private static final Map<Class<?>, JavaType> LIST_TYPES = new ConcurrentHashMap<>();

  private final CryptoComRequestIdGenerator requestIdGenerator = new CryptoComRequestIdGenerator();
  private final AtomicLong connectionGeneration = new AtomicLong();
  private final AtomicInteger reconnectAttempts = new AtomicInteger();
  private final Map<Long, String> pendingSubscriptions = new ConcurrentHashMap<>();
  private final Set<String> activeChannels = ConcurrentHashMap.newKeySet();

  public CryptoComStreamingService(String apiUrl) {
    super(apiUrl);
    subscribeConnectionSuccess().subscribe(ignored -> reconnectAttempts.set(0));
  }

  protected long nextRequestId() {
    return requestIdGenerator.next();
  }

  /** Generation of the last successfully established connection; 0 until the first connection. */
  public long getConnectionGeneration() {
    return connectionGeneration.get();
  }

  /** True when this service is attached to a known physical connection (not stale/never seen). */
  public boolean isCurrentConnection() {
    long generation = connectionGeneration.get();
    return generation > 0 && generation == getGeneration();
  }

  @Override
  public void resubscribeChannels() {
    // Called by the framework for every successful (re)connection; capture the generation so
    // consumers can tell responses of a superseded socket apart from the active one. Channel
    // confirmations are per-connection state: a superseded socket's confirmations must never
    // count towards the new connection, so the tracked state is cleared here and rebuilt from
    // the resubscribed channels.
    connectionGeneration.set(getGeneration());
    activeChannels.clear();
    pendingSubscriptions.clear();
    super.resubscribeChannels();
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    String method = SUBSCRIBE_METHOD;
    long id = nextRequestId();
    ObjectNode message = objectMapper.createObjectNode();
    message.put("id", id);
    message.put("method", method);
    ObjectNode params = message.putObject("params");
    params.putArray("channels").add(channelName);
    if (channelName.startsWith("book.")) {
      // Official book contract: subscribe to the combined snapshot-and-update feed (the server
      // sends a full snapshot first and incremental updates afterwards; the assembler validates
      // the u/pu sequence chain and rebuilds from a fresh snapshot when it breaks).
      params.put("book_subscription_type", "SNAPSHOT_AND_UPDATE");
    }
    pendingSubscriptions.put(id, channelName);
    return objectMapper.writeValueAsString(message);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    return buildSubscriptionMessage(UNSUBSCRIBE_METHOD, channelName);
  }

  private String buildSubscriptionMessage(String method, String channelName) throws IOException {
    ObjectNode message = objectMapper.createObjectNode();
    long id = nextRequestId();
    message.put("id", id);
    message.put("method", method);
    message.putObject("params").putArray("channels").add(channelName);
    // Track both subscribe and unsubscribe ids so confirmations can be correlated with the
    // channel and (for subscribe) promoted to active.
    pendingSubscriptions.put(id, channelName);
    return objectMapper.writeValueAsString(message);
  }

  /** True once the server confirmed (code 0) the subscription to the given channel. */
  public boolean isChannelActive(String channelName) {
    return activeChannels.contains(channelName);
  }

  /** Channels confirmed active by the server since the current connection was established. */
  public Set<String> getActiveChannels() {
    return Collections.unmodifiableSet(activeChannels);
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) throws IOException {
    JsonNode subscription = message.at("/result/subscription");
    if (subscription.isMissingNode()) {
      throw new IOException("Message has no subscription channel: " + message);
    }
    return subscription.asText();
  }

  /** Converts the {@code result.data} array of a push message envelope, caching the list type. */
  public <T> List<T> extractData(JsonNode envelope, Class<T> elementType) {
    JsonNode data = envelope.at("/result/data");
    if (data.isMissingNode() || data.isNull()) {
      return Collections.emptyList();
    }
    JavaType listType =
        LIST_TYPES.computeIfAbsent(
            elementType,
            type -> objectMapper.getTypeFactory().constructCollectionType(List.class, type));
    return objectMapper.convertValue(data, listType);
  }

  @Override
  protected void handleMessage(JsonNode message) {
    String method = message.path("method").asText("");
    if (HEARTBEAT_METHOD.equals(method)) {
      respondToHeartbeat(message.path("id").asLong());
      return;
    }
    if (!message.has("result")) {
      // Plain subscribe/unsubscribe (and, in the private service, public/auth) confirmations
      // carry no result.data; correlate them with the pending request ids.
      handleConfirmation(message, method);
      return;
    }
    super.handleMessage(message);
  }

  private void handleConfirmation(JsonNode message, String method) {
    int code = message.path("code").asInt(-1);
    if (SUBSCRIBE_METHOD.equals(method) || UNSUBSCRIBE_METHOD.equals(method)) {
      String channel = pendingSubscriptions.remove(message.path("id").asLong(-1L));
      if (channel == null) {
        if (code != 0) {
          LOG.warn("Crypto.com WebSocket error response: {}", message);
        }
        return;
      }
      if (code != 0) {
        LOG.warn("Crypto.com WebSocket rejected {} {}: {}", method, channel, message);
        return;
      }
      if (SUBSCRIBE_METHOD.equals(method)) {
        activeChannels.add(channel);
      } else {
        activeChannels.remove(channel);
      }
      LOG.info("Crypto.com WebSocket {} confirmed: {}", method, channel);
      return;
    }
    if (code != 0) {
      LOG.warn("Crypto.com WebSocket error response: {}", message);
    }
  }

  private void respondToHeartbeat(long id) {
    sendObjectMessage(buildHeartbeatResponse(id));
  }

  /** {@code public/respond-heartbeat} echo carrying the server-provided heartbeat id. */
  ObjectNode buildHeartbeatResponse(long id) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put("id", id);
    response.put("method", HEARTBEAT_RESPONSE_METHOD);
    return response;
  }

  /**
   * Bounded reconnect: consecutive scheduling is capped at {@value #MAX_RECONNECT_ATTEMPTS} after
   * which the service stops reconnecting (transports then report dead via {@code isSocketOpen()})
   * and {@link #onReconnectBudgetExhausted()} is invoked. The budget resets on every successful
   * connection.
   */
  @Override
  protected final void scheduleReconnect() {
    if (reconnectAttempts.incrementAndGet() > MAX_RECONNECT_ATTEMPTS) {
      LOG.error(
          "Giving up on reconnecting the Crypto.com WebSocket after {} attempts",
          MAX_RECONNECT_ATTEMPTS);
      onReconnectBudgetExhausted();
      return;
    }
    doScheduleReconnect();
  }

  /** Schedules the actual reconnection; separated so exception handling stays testable. */
  protected void doScheduleReconnect() {
    super.scheduleReconnect();
  }

  /** Hook invoked when the bounded reconnect budget has been exhausted. */
  protected void onReconnectBudgetExhausted() {
    // no-op: transports report closed; subclasses may react.
  }
}