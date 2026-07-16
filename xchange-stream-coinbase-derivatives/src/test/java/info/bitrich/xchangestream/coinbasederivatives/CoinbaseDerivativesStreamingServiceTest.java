package info.bitrich.xchangestream.coinbasederivatives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoinbaseDerivativesStreamingServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void correlatesNumericIdsAndRejectsPriorConnectionResponses() throws Exception {
    CapturingService service = service(() -> "jwt");
    service.beginConnectionGeneration();

    TestObserver<JsonNode> response =
        service.request("public/test", MAPPER.createObjectNode(), true).test();
    JsonNode outbound = MAPPER.readTree(service.lastMessage());
    assertTrue(outbound.get("id").isIntegralNumber());

    service.beginConnectionGeneration();
    response.assertError(CoinbaseDerivativesStreamException.class);

    TestObserver<Throwable> errors = service.protocolErrors().test();
    service.handleProtocolMessage(
        MAPPER.readTree(
            "{\"jsonrpc\":\"2.0\",\"id\":" + outbound.get("id").asLong() + ",\"result\":{}}"));
    assertEquals(1, errors.values().size());
    assertTrue(errors.values().get(0).getMessage().contains("stale or unknown"));
  }

  @Test
  void generatesFreshJwtForEveryAuthExchangeUsingTokenField() throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    CapturingService service = service(() -> "jwt-" + sequence.incrementAndGet());
    service.beginConnectionGeneration();

    TestObserver<Void> first = service.reauthenticate().test();
    JsonNode firstRequest = MAPPER.readTree(service.lastMessage());
    assertEquals("coinbase_cdp", firstRequest.path("params").path("grant_type").asText());
    assertEquals("jwt-1", firstRequest.path("params").path("token").asText());
    assertFalse(firstRequest.path("params").has("signed_jwt"));
    service.success(firstRequest.path("id").asLong());
    first.assertComplete();

    TestObserver<Void> second = service.reauthenticate().test();
    JsonNode secondRequest = MAPPER.readTree(service.lastMessage());
    assertEquals("jwt-2", secondRequest.path("params").path("token").asText());
    service.success(secondRequest.path("id").asLong());
    second.assertComplete();
  }

  @Test
  void answersHeartbeatTestRequests() throws Exception {
    CapturingService service = service(() -> "jwt");
    service.beginConnectionGeneration();

    service.handleProtocolMessage(
        MAPPER.readTree(
            "{\"jsonrpc\":\"2.0\",\"method\":\"heartbeat\",\"params\":{\"type\":\"test_request\"}}"));

    assertEquals("public/test", MAPPER.readTree(service.lastMessage()).path("method").asText());
  }

  @Test
  void queuesPrivateSubscriptionUntilAuthenticationThenResubscribes() throws Exception {
    CapturingService service = service(() -> "jwt");
    service.beginConnectionGeneration();

    service.subscribePrivateChannel("user.portfolio.USDC").test();
    assertTrue(service.messages.isEmpty());

    TestObserver<Void> authentication = service.reauthenticate().test();
    JsonNode authRequest = MAPPER.readTree(service.lastMessage());
    service.success(authRequest.path("id").asLong());
    authentication.assertComplete();

    JsonNode subscription = MAPPER.readTree(service.lastMessage());
    assertEquals("private/subscribe", subscription.path("method").asText());
    assertEquals(
        "user.portfolio.USDC", subscription.path("params").path("channels").get(0).asText());
  }

  @Test
  void deduplicatesTradesAndSurfacesOrderBookGaps() throws Exception {
    CapturingService service = service(() -> "jwt");
    service.beginConnectionGeneration();
    TestObserver<JsonNode> observer =
        service.subscribePrivateChannel("book.BTC_USDC-PERPETUAL.100ms").test();

    service.handleProtocolMessage(
        notification(
            "book.BTC_USDC-PERPETUAL.100ms",
            "{\"type\":\"snapshot\",\"change_id\":10,\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"trade_id\":\"t1\",\"timestamp\":1}"));
    service.handleProtocolMessage(
        notification(
            "book.BTC_USDC-PERPETUAL.100ms",
            "{\"type\":\"snapshot\",\"change_id\":10,\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"trade_id\":\"t1\",\"timestamp\":1}"));
    assertEquals(1, observer.values().size());

    service.handleProtocolMessage(
        notification(
            "book.BTC_USDC-PERPETUAL.100ms",
            "{\"type\":\"change\",\"change_id\":12,\"prev_change_id\":9,\"instrument_name\":\"BTC_USDC-PERPETUAL\"}"));
    observer.assertError(CoinbaseDerivativesStreamGapException.class);
  }

  @Test
  void cancelOnDisconnectDefaultsOffAndCreditBackoffIsBounded() throws Exception {
    CoinbaseDerivativesStreamConfiguration configuration =
        new CoinbaseDerivativesStreamConfiguration(() -> "jwt");
    assertFalse(configuration.isCancelOnDisconnect());

    CapturingService service = new CapturingService(configuration);
    service.beginConnectionGeneration();
    service.handleProtocolMessage(
        MAPPER.readTree("{\"error\":{\"message\":\"rate credits exhausted\"}}"));
    assertTrue(service.currentCreditBackoff().toSeconds() >= 1);
    assertTrue(service.currentCreditBackoff().toSeconds() <= 30);
    assertTrue(service.remainingCreditDelayMillis() > 0);
  }

  private CapturingService service(java.util.function.Supplier<String> jwtSupplier) {
    return new CapturingService(new CoinbaseDerivativesStreamConfiguration(jwtSupplier));
  }

  private JsonNode notification(String channel, String data) throws Exception {
    return MAPPER.readTree(
        "{\"jsonrpc\":\"2.0\",\"method\":\"subscription\",\"params\":{\"channel\":\""
            + channel
            + "\",\"data\":"
            + data
            + "}}}");
  }

  private static final class CapturingService extends CoinbaseDerivativesStreamingService {
    private final List<String> messages = new ArrayList<>();

    private CapturingService(CoinbaseDerivativesStreamConfiguration configuration) {
      super("ws://localhost", configuration);
    }

    @Override
    public void sendMessage(String message) {
      if (message != null) {
        messages.add(message);
      }
    }

    @Override
    public boolean isSocketOpen() {
      return true;
    }

    private String lastMessage() {
      return messages.get(messages.size() - 1);
    }

    private void success(long id) throws Exception {
      handleProtocolMessage(
          MAPPER.readTree("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}"));
    }
  }
}
