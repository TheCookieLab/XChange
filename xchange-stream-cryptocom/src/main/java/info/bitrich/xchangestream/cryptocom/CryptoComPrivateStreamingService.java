package info.bitrich.xchangestream.cryptocom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.knowm.xchange.cryptocom.CryptoComDigest;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Crypto.com Exchange v1 user WebSocket feed requires signing in with {@code public/auth}
 * immediately after connecting, before any {@code user.*} channel can be subscribed. Following the
 * same connect -&gt; authenticate -&gt; (re)subscribe sequence used for reconnects, {@link
 * #resubscribeChannels()} - which the framework calls right after every successful connection - is
 * overridden to reset the authentication state and send the login message; {@code user.*}
 * channels that were active before a drop are (re)subscribed once the auth confirmation for the
 * current connection arrives, see {@link #handleMessage(JsonNode)}.
 *
 * <p>Authentication confirmations are correlated with the auth request id of the current
 * connection generation: a reconnect issues a fresh auth request, and any confirmation carrying a
 * different (stale) id cannot flip {@link #isAuthenticated()}, so late responses of a superseded
 * socket never re-open the private data plane.
 */
public class CryptoComPrivateStreamingService extends CryptoComStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoComPrivateStreamingService.class);
  private static final String AUTH_METHOD = "public/auth";

  private final String apiKey;
  private final String apiSecret;
  private final AtomicBoolean authenticated = new AtomicBoolean();
  private final AtomicLong pendingAuthId = new AtomicLong(-1L);

  public CryptoComPrivateStreamingService(String apiUrl, String apiKey, String apiSecret) {
    super(apiUrl);
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
  }

  /** True once the server confirmed {@code public/auth} (code 0) for the current connection. */
  public boolean isAuthenticated() {
    return authenticated.get();
  }

  @Override
  public void resubscribeChannels() {
    // Every (re)connection starts unauthenticated; only the matching auth confirmation id of
    // this connection may flip the flag (stale-generation acks are ignored in handleMessage).
    authenticated.set(false);
    pendingAuthId.set(-1L);
    super.resubscribeChannels();
    sendAuthMessage();
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
      }
      return;
    }
    super.handleMessage(message);
  }
}