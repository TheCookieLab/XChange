package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.CryptoComRequestException;
import org.knowm.xchange.cryptocom.dto.CryptoComRetryClass;
import org.knowm.xchange.cryptocom.dto.CryptoComTransport;

/**
 * Behavioral tests for {@link CryptoComPrivateStreamingService}: authentication id correlation
 * (stale confirmations of a superseded connection never open the private plane), request
 * confirmation correlation, and the trading-safety rule that a connection loss fails pending
 * non-replayable requests explicitly and never auto-resends them. Deterministic fixtures only -
 * no network or live calls.
 */
public class CryptoComPrivateStreamingServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Records outgoing messages (auth, requests, resubscribes) instead of touching the network. */
  static final class RecordingPrivateService extends CryptoComPrivateStreamingService {

    final List<ObjectNode> sent = new ArrayList<>();

    RecordingPrivateService() {
      super("wss://stream.crypto.com/exchange/v1/user", "api-key", "api-secret");
      // Emulate the first successful connection: the framework resubscribes channels, which
      // sends the signed public/auth request captured by the recording transport.
      resubscribeChannels();
    }

    @Override
    protected void sendObjectMessage(Object message) {
      sent.add((ObjectNode) message);
    }

    RecordingPrivateService deliver(JsonNode message) {
      handleMessage(message);
      return this;
    }

    ObjectNode lastSent() {
      return sent.get(sent.size() - 1);
    }
  }

  @Test
  public void testAuthConfirmationWithPendingIdAuthenticates() {
    // given
    RecordingPrivateService service = new RecordingPrivateService();

    // when: a fresh (re)connection authenticates
    long authId = service.lastSent().path("id").asLong();
    assertThat(service.lastSent().path("method").asText()).isEqualTo("public/auth");
    service.deliver(message("{\"id\":" + authId + ",\"method\":\"public/auth\",\"code\":0}"));

    // then
    assertThat(service.isAuthenticated()).isTrue();
  }

  @Test
  public void testStaleAuthConfirmationCannotAuthenticate() {
    // given
    RecordingPrivateService service = new RecordingPrivateService();
    long authId = service.lastSent().path("id").asLong();

    // when: a confirmation for the *previous* connection's auth id arrives late
    service.deliver(
        message("{\"id\":" + (authId - 100) + ",\"method\":\"public/auth\",\"code\":0}"));

    // then: the current connection stays unauthenticated (generation-aware correlation)
    assertThat(service.isAuthenticated()).isFalse();

    // and the correct id still authenticates
    service.deliver(message("{\"id\":" + authId + ",\"method\":\"public/auth\",\"code\":0}"));
    assertThat(service.isAuthenticated()).isTrue();
  }

  @Test
  public void testAuthFailureFailsPendingRequestsExplicitly() {
    // given: a request is already in flight before the auth failure is processed
    RecordingPrivateService service = new RecordingPrivateService();
    TestObserver<JsonNode> observer =
        service.sendRequest("private/create-order", params("BTC_USDT"), false).test();

    // when: resubscribe triggers auth which the server rejects
    service.resubscribeChannels();
    long pendingAuthId = pendingAuthId(service);
    service.deliver(
        message(
            "{\"id\":"
                + pendingAuthId
                + ",\"method\":\"public/auth\",\"code\":10002,\"message\":\"bad signature\"}"));

    // then: the request fails explicitly, never silently stalls or gets re-sent
    assertThat(service.isAuthenticated()).isFalse();
    final Throwable[] failed = new Throwable[1];
    observer.assertError(
        t -> t instanceof CryptoComRequestException && (failed[0] = t) != null);
    CryptoComRequestException requestException = (CryptoComRequestException) failed[0];
    assertThat(requestException.getTransport()).isEqualTo(CryptoComTransport.WEBSOCKET);
    assertThat(requestException.getRetryClass()).isEqualTo(CryptoComRetryClass.NONE);
    assertThat(requestException.getMessage()).contains("authentication failed");
  }

  @Test
  public void testSendRequestCorrelatesConfirmationByRequestId() {
    // given
    RecordingPrivateService service = new RecordingPrivateService();
    TestObserver<JsonNode> observer =
        service.sendRequest("private/cancel-order", params("18342311"), true).test();

    // then: the signed request went out with the expected envelope
    ObjectNode request = service.lastSent();
    assertThat(request.path("method").asText()).isEqualTo("private/cancel-order");
    long requestId = request.path("id").asLong();
    assertThat(requestId).isPositive();
    assertThat(request.path("api_key").asText()).isEqualTo("api-key");
    assertThat(request.path("sig").asText()).isNotBlank();
    assertThat(request.path("params").isObject()).isTrue();

    // when: the server confirms with the matching id
    service.deliver(
        message(
            "{\"id\":"
                + requestId
                + ",\"method\":\"private/cancel-order\",\"code\":0,\"result\":{\"data\":[{\"order_id\":\"18342311\"}]}}"));

    // then: the caller sees the confirmation envelope
    observer.assertNoErrors().assertComplete();
    assertThat(observer.values()).hasSize(1);
    assertThat(observer.values().get(0).at("/result/data/0/order_id").asText())
        .isEqualTo("18342311");
  }

  @Test
  public void testSendRequestRejectsWithProviderError() {
    // given
    RecordingPrivateService service = new RecordingPrivateService();
    TestObserver<JsonNode> observer =
        service.sendRequest("private/get-order-detail", params("18342311"), true).test();
    long requestId = service.lastSent().path("id").asLong();

    // when: the server rejects the request
    service.deliver(
        message(
            "{\"id\":"
                + requestId
                + ",\"method\":\"private/get-order-detail\",\"code\":10014,\"message\":\"no such order\"}"));

    // then
    final Throwable[] rejected = new Throwable[1];
    observer.assertError(
        t -> t instanceof CryptoComRequestException && (rejected[0] = t) != null);
    CryptoComRequestException error = (CryptoComRequestException) rejected[0];
    assertThat(error.getRequestId()).isEqualTo(requestId);
    assertThat(error.getRetryClass()).isEqualTo(CryptoComRetryClass.NONE);
    assertThat(error.getProviderCode()).isEqualTo(10014);
  }

  @Test
  public void testDisconnectFailsPendingAndResubscriptionNeverResendsRequests() {
    // given: a non-replayable trading request whose confirmation never arrives
    RecordingPrivateService service = new RecordingPrivateService();
    TestObserver<JsonNode> observer =
        service.sendRequest("private/create-order", params("BTC_USDT"), false).test();
    long requestId = service.lastSent().path("id").asLong();
    assertThat(requestId).isPositive();

    // when: the socket drops before confirmation - the exchange may have executed the order
    service.disconnect().test().assertComplete();

    // then: it fails explicitly and must never be re-sent by reconnect logic
    final Throwable[] failed = new Throwable[1];
    observer.assertError(
        t -> t instanceof CryptoComRequestException && (failed[0] = t) != null);
    CryptoComRequestException error = (CryptoComRequestException) failed[0];
    assertThat(error.getMessage()).contains("disconnected");
    assertThat(error.getMessage()).contains("NOT re-sent");
    assertThat(error.getRetryClass()).isEqualTo(CryptoComRetryClass.NONE);

    // and a reconnect only re-subscribes channels and re-authenticates - no request replay
    List<ObjectNode> preReconnect = new ArrayList<>(service.sent);
    service.resubscribeChannels();
    for (int i = preReconnect.size(); i < service.sent.size(); i++) {
      assertThat(service.sent.get(i).path("method").asText())
          .isIn("public/auth", "subscribe", "unsubscribe");
    }
  }

  @Test
  public void testReplayableRequestFailsExplicitlyWithReissueGuidance() {
    // given
    RecordingPrivateService service = new RecordingPrivateService();
    TestObserver<JsonNode> observer =
        service.sendRequest("private/get-order-detail", params("18342311"), true).test();

    // when
    service.disconnect().test().assertComplete();

    // then: read-only requests fail the same way, with guidance to re-issue explicitly
    final Throwable[] failed = new Throwable[1];
    observer.assertError(t -> (failed[0] = t) != null);
    assertThat(failed[0].getMessage()).contains("replayable-read-only");
    assertThat(failed[0].getMessage()).contains("re-issue");
  }

  /** Pulls the auth id out of the recorded resubscribe auth message. */
  private long pendingAuthId(RecordingPrivateService service) {
    for (int i = service.sent.size() - 1; i >= 0; i--) {
      if ("public/auth".equals(service.sent.get(i).path("method").asText())) {
        return service.sent.get(i).path("id").asLong();
      }
    }
    throw new AssertionError("no auth message sent");
  }

  private Map<String, Object> params(String value) {
    Map<String, Object> params = new HashMap<>();
    params.put("order_id", value);
    params.put("instrument_name", "BTC_USDT");
    return params;
  }

  private JsonNode message(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}