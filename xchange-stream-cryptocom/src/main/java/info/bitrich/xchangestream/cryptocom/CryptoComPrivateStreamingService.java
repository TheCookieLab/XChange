package info.bitrich.xchangestream.cryptocom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.knowm.xchange.cryptocom.CryptoComDigest;
import org.knowm.xchange.cryptocom.dto.CryptoComRequestException;
import org.knowm.xchange.cryptocom.dto.CryptoComRetryClass;
import org.knowm.xchange.cryptocom.dto.CryptoComTransport;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Crypto.com Exchange v1 user WebSocket feed requires signing in with {@code public/auth}
 * immediately after connecting, before any {@code user.*} channel can be subscribed. Following the
 * same connect -&gt; authenticate -&gt; (re)subscribe sequence used for reconnects, {@link
 * #resubscribeChannels()} - which the framework calls right after every successful connection - is
 * overridden to reset the authentication state and send the login message; {@code user.*} channels
 * that were active before a drop are (re)subscribed once the auth confirmation for the current
 * connection arrives, see {@link #handleMessage(JsonNode)}.
 *
 * <p>Authentication confirmations are correlated with the auth request id of the current
 * connection generation: a reconnect issues a fresh auth request, and any confirmation carrying a
 * different (stale) id cannot flip {@link #isAuthenticated()}, so late responses of a superseded
 * socket never re-open the private data plane.
 *
 * <p><strong>Trading request correlation.</strong> User-side requests ({@code private/...}
 * methods) travel over the same socket and are confirmed by envelope request id. {@link
 * #sendRequest(String, Map, boolean)} correlates pending requests with their confirmation and
 * <em>fails them explicitly</em> when the connection is lost before confirmation: a non-replayable
 * trading request may have reached the exchange, so its outcome is unknown and it is never
 * auto-resent - callers must reconcile via the REST APIs. Reconnect logic only re-sends channel
 * subscriptions ({@code resubscribeChannels()}), never requests.
 */
public class CryptoComPrivateStreamingService extends CryptoComStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoComPrivateStreamingService.class);
  private static final String AUTH_METHOD = "public/auth";

  private final String apiKey;
  private final String apiSecret;
  private final AtomicBoolean authenticated = new AtomicBoolean();
  private final AtomicLong pendingAuthId = new AtomicLong(-1L);
  private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

  public CryptoComPrivateStreamingService(String apiUrl, String apiKey, String apiSecret) {
    super(apiUrl);
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    subscribeDisconnect().subscribe(ignored -> failPendingRequests(true));
  }

  /** True once the server confirmed {@code public/auth} (code 0) for the current connection. */
  public boolean isAuthenticated() {
    return authenticated.get();
  }

  /**
   * Sends a signed {@code private/...} request over the user WebSocket and emits its confirmation
   * envelope once the server replies with the request id. When {@code replayable} is false the
   * request must not be repeated on a reconnect (for example order placement): on any connection
   * loss before confirmation the observable fails with {@link CryptoComRequestException} and the
   * request is never re-sent - reconnect only re-subscribes channels. Replayable requests
   * (idempotent reads) fail the same way but the caller may re-issue them explicitly.
   */
  public Observable<JsonNode> sendRequest(String method, Map<String, Object> params, boolean replayable) {
    long id = nextRequestId();
    long nonce = System.currentTimeMillis();
    String signature = CryptoComDigest.signature(method, id, apiKey, nonce, params, apiSecret);

    ObjectNode message = objectMapper.createObjectNode();
    message.put("id", id);
    message.put("method", method);
    message.put("api_key", apiKey);
    message.put("sig", signature);
    message.put("nonce", nonce);
    if (params != null && !params.isEmpty()) {
      message.set("params", objectMapper.valueToTree(params));
    }

    PendingRequest pending = new PendingRequest(id, method, replayable);
    pendingRequests.put(id, pending);
    sendObjectMessage(message);
    return pending.subject;
  }

  @Override
  public void resubscribeChannels() {
    // Every (re)connection starts unauthenticated; only the matching auth confirmation id of
    // this connection may flip the flag (stale-generation acks are ignored in handleMessage).
    // Pending trading requests are deliberately NOT re-sent: resubscription covers channels only.
    authenticated.set(false);
    pendingAuthId.set(-1L);
    super.resubscribeChannels();
    sendAuthMessage();
  }

  @Override
  public Completable disconnect() {
    failPendingRequests(true);
    return super.disconnect();
  }

  private void sendAuthMessage() {
    long id = nextRequestId();
    long nonce = System.currentTimeMillis();
    String signature =
        CryptoComDigest.signature(
            AUTH_METHOD, id, apiKey, nonce, Collections.emptyMap(), apiSecret);

    ObjectNode message = objectMapper.createObjectNode();
    message.put("id", id);
    message.put("method", AUTH_METHOD);
    message.put("api_key", apiKey);
    message.put("sig", signature);
    message.put("nonce", nonce);
    pendingAuthId.set(id);
    sendObjectMessage(message);
  }

  @Override
  protected void handleMessage(JsonNode message) {
    if (AUTH_METHOD.equals(message.path("method").asText(""))) {
      long authId = message.path("id").asLong(-1L);
      if (authId != pendingAuthId.get()) {
        LOG.debug(
            "Ignoring stale auth confirmation id={} for pending auth id={}",
            authId,
            pendingAuthId.get());
        return;
      }
      if (message.path("code").asInt(-1) == 0) {
        LOG.info("Crypto.com user WebSocket authenticated");
        authenticated.set(true);
        super.resubscribeChannels();
      } else {
        ExchangeSecurityException authFailure =
            new ExchangeSecurityException(
                "Crypto.com user WebSocket authentication failed: " + message);
        LOG.error(authFailure.getMessage());
        authenticated.set(false);
        // Surface the failure to any already-subscribed user.* channels instead of leaving
        // their observables silently waiting forever for data that will never arrive.
        channels.keySet().forEach(channel -> handleChannelError(channel, authFailure));
        // The socket is unusable for user requests too - fail them explicitly.
        failPendingRequests(false);
      }
      return;
    }

    // Correlate user request confirmations by their envelope id (pushes carry id=-1 and are
    // never pending, so they cannot collide).
    long id = message.path("id").asLong(-1L);
    PendingRequest pending = id > 0 ? pendingRequests.get(id) : null;
    if (pending != null) {
      pendingRequests.remove(id);
      int code = message.path("code").asInt(-1);
      if (code == 0) {
        pending.subject.onNext(message);
        pending.subject.onComplete();
      } else {
        pending.subject.onError(
            CryptoComRequestException.builder()
                .requestId(id)
                .method(pending.method)
                .transport(CryptoComTransport.WEBSOCKET)
                .providerCode(code)
                .providerMessage(message.path("message").asText(null))
                .retryClass(CryptoComRetryClass.NONE)
                .build());
      }
      return;
    }

    super.handleMessage(message);
  }

  /** Fails every pending request: the socket is gone, so no confirmation will ever arrive. */
  private void failPendingRequests(boolean disconnected) {
    for (Map.Entry<Long, PendingRequest> entry : pendingRequests.entrySet()) {
      PendingRequest pending = entry.getValue();
      if (pendingRequests.remove(entry.getKey(), pending)) {
        String reason =
            disconnected
                ? "Crypto.com WebSocket disconnected before request id="
                    + pending.id
                    + " ["
                    + pending.method
                    + "] was confirmed"
                : "Crypto.com WebSocket authentication failed before request id="
                    + pending.id
                    + " ["
                    + pending.method
                    + "] was confirmed";
        String outcome =
            pending.replayable
                ? "The request is replayable-read-only and was not re-sent; re-issue it explicitly if still required."
                : "The request outcome is unknown and it was NOT re-sent (non-replayable); reconcile it via the REST APIs before retrying.";
        pending.subject.onError(
            CryptoComRequestException.builder()
                .requestId(pending.id)
                .method(pending.method)
                .transport(CryptoComTransport.WEBSOCKET)
                .retryClass(CryptoComRetryClass.NONE)
                .message(reason + ". " + outcome)
                .build());
      }
    }
  }

  /** A user request whose confirmation envelope has not arrived yet. */
  private static final class PendingRequest {
    private final long id;
    private final String method;
    private final boolean replayable;
    private final PublishSubject<JsonNode> subject = PublishSubject.create();

    private PendingRequest(long id, String method, boolean replayable) {
      this.id = id;
      this.method = method;
      this.replayable = replayable;
    }
  }
}