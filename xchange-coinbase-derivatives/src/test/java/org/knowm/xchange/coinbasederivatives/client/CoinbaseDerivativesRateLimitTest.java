package org.knowm.xchange.coinbasederivatives.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.TestKeys;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesAccessTokenProvider;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesJwtGenerator;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Dispatch;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Reason;

/** Offline tests of the Global Derivatives rate-limit policy and its transport adoption. */
class CoinbaseDerivativesRateLimitTest {
  private static final String ACCOUNT_SUMMARY = "private/get_account_summary";
  private static final String BUY = "private/buy";
  private static final String CANCEL = "private/cancel";
  private static final String AUTH = "public/auth";
  private static final String TICKER = "public/ticker";
  private static final String TOKEN_RESULT =
      "{\"access_token\":\"token\",\"token_type\":\"bearer\",\"expires_in\":900,"
          + "\"scope\":\"trade\"}";

  private final ObjectMapper json = new ObjectMapper();
  private WireMockServer server;
  private RateLimitPolicy policy;
  private RateLimitContext context;
  private CoinbaseDerivativesJsonRpcTransport transport;
  private final List<String> mintedJwts = new ArrayList<>();

  @BeforeEach
  void startServer() throws Exception {
    server = new WireMockServer(options().dynamicPort().globalTemplating(true));
    server.start();
    policy =
        CoinbaseDerivativesRateLimitPolicy.create()
            .withFallbackBackoff(Duration.ofMillis(1), Duration.ofMillis(1), 0.0);
    context = new RateLimitContext();
    transport =
        new CoinbaseDerivativesJsonRpcTransport(
            URI.create(server.baseUrl() + "/"), policy, context, "user-1");
    transport.setAccessTokenProvider(
        new CoinbaseDerivativesAccessTokenProvider(
            new CoinbaseDerivativesJwtGenerator("key", TestKeys.newEcPrivateKeyPem()),
            freshJwt ->
                transport.authenticate(
                    () -> {
                      String jwt = freshJwt.get();
                      mintedJwts.add(jwt);
                      return jwt;
                    })));
  }

  @AfterEach
  void stopServer() {
    context.close();
    server.stop();
  }

  @Test
  void policyClassifiesMethodsWithDistinctCostsAndPriorities() {
    RateLimitOperation ticker = classify(TICKER, false);
    RateLimitOperation instruments = classify("public/get_instruments", false);
    RateLimitOperation auth = classify(AUTH, false);
    RateLimitOperation read = classify(ACCOUNT_SUMMARY, true);
    RateLimitOperation cancel = classify(CANCEL, true);
    RateLimitOperation buy = classify(BUY, true);

    assertEquals(RateLimitPriority.MARKET_DATA, ticker.getPriority());
    assertEquals(
        Map.of(CoinbaseDerivativesRateLimitPolicy.PUBLIC_CREDITS, 500L), ticker.getRequirements());
    assertEquals(RateLimitPriority.MARKET_DATA, instruments.getPriority());
    assertEquals(
        Map.of(CoinbaseDerivativesRateLimitPolicy.INSTRUMENTS, 1L), instruments.getRequirements());
    assertEquals(RateLimitPriority.EXECUTION, auth.getPriority());
    assertEquals(
        Map.of(
            CoinbaseDerivativesRateLimitPolicy.PUBLIC_CREDITS,
            500L,
            CoinbaseDerivativesRateLimitPolicy.USER_HOURLY,
            1L),
        auth.getRequirements());
    assertEquals(RateLimitPriority.EXECUTION, read.getPriority());
    assertEquals(
        Map.of(
            CoinbaseDerivativesRateLimitPolicy.CREDITS,
            500L,
            CoinbaseDerivativesRateLimitPolicy.USER_HOURLY,
            1L),
        read.getRequirements());
    assertEquals(
        Map.of(
            CoinbaseDerivativesRateLimitPolicy.MATCHING,
            1L,
            CoinbaseDerivativesRateLimitPolicy.USER_HOURLY,
            1L),
        buy.getRequirements());
    assertEquals(buy.getRequirements(), cancel.getRequirements());

    assertTrue(ticker.isReplayOnRateLimit());
    assertTrue(read.isReplayOnRateLimit());
    assertTrue(cancel.isReplayOnRateLimit());
    assertFalse(buy.isReplayOnRateLimit());
    assertNotNull(policy.getBudget(CoinbaseDerivativesRateLimitPolicy.USER_HOURLY));
    assertNull(
        policy.classify(new RateLimitRequest("public/not_declared", false)));
  }

  @Test
  void publicReadPrivateReadAuthAndPlacementShareOneContextAndOnePostEndpoint() throws Exception {
    stubSequence(TICKER, result("{}"));
    stubSequence(AUTH, result(TOKEN_RESULT));
    stubSequence(ACCOUNT_SUMMARY, result("{}"));
    stubSequence(BUY, result("{}"));
    stubSequence(CANCEL, result("{}"));

    transport.callPublic(TICKER, Map.of(), Map.class);
    transport.callPrivate(ACCOUNT_SUMMARY, Map.of(), Map.class, ReplaySafety.READ);
    transport.callPrivate(BUY, Map.of("amount", 1), Map.class, ReplaySafety.PLACEMENT);
    transport.callPrivate(CANCEL, Map.of(), Map.class, ReplaySafety.IDEMPOTENT_CANCELLATION);

    // ticker, the lazy public/auth, account summary, buy, cancel: one admission per wire attempt.
    assertEquals(5, context.diagnostics().getAdmissions());
    assertEquals(0, context.diagnostics().getRetries());
    assertEquals(
        CoinbaseDerivativesRateLimitPolicy.VERSION,
        context.diagnostics().getPolicyVersions().get(CoinbaseDerivativesRateLimitPolicy.NAMESPACE));
    assertEquals(5, server.findAll(postRequestedFor(urlEqualTo("/"))).size());
    for (String method : List.of(TICKER, AUTH, ACCOUNT_SUMMARY, BUY, CANCEL)) {
      assertEquals(1, count(method), method);
    }
    assertEquals(1, context.diagnostics().getPolicyVersions().size());
  }

  @Test
  void placementAcceptedThenResponseLostIsSentOnceAndAmbiguous() throws Exception {
    stubSequence(AUTH, result(TOKEN_RESULT));
    stubSequence(BUY, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    CoinbaseDerivativesException failure =
        assertThrows(
            CoinbaseDerivativesException.class,
            () ->
                transport.callPrivate(
                    BUY, Map.of("label", "once"), Map.class, ReplaySafety.PLACEMENT));

    assertEquals(RetryClassification.AMBIGUOUS, failure.getRetryClassification());
    assertEquals(1, count(BUY));
    assertEquals(0, context.diagnostics().getRetries());
  }

  @Test
  void readRejectedWithJsonRpcTooManyRequestsIsReplayedByTheCore() throws Exception {
    stubSequence(AUTH, result(TOKEN_RESULT));
    stubSequence(ACCOUNT_SUMMARY, error(10028, "too_many_requests"), result("{\"ok\":true}"));

    Map<?, ?> result =
        transport.callPrivate(ACCOUNT_SUMMARY, Map.of(), Map.class, ReplaySafety.READ);

    assertEquals(Map.of("ok", true), result);
    assertEquals(2, count(ACCOUNT_SUMMARY));
    assertEquals(1, context.diagnostics().getRetries());
    assertEquals(1, context.diagnostics().getRatePressureEvents());
    // auth + rejected read + replayed read: every wire attempt admitted once
    assertEquals(3, context.diagnostics().getAdmissions());
  }

  @Test
  void publicReadRejectedWithHttp429IsReplayedByTheCore() throws Exception {
    stubSequence(TICKER, aResponse().withStatus(429), result("{\"ok\":true}"));

    Map<?, ?> result = transport.callPublic(TICKER, Map.of(), Map.class);

    assertEquals(Map.of("ok", true), result);
    assertEquals(2, count(TICKER));
    assertEquals(1, context.diagnostics().getRetries());
    assertEquals(2, context.diagnostics().getAdmissions());
  }

  @Test
  void cancellationRejectedWithTooManyRequestsIsReplayed() throws Exception {
    stubSequence(AUTH, result(TOKEN_RESULT));
    stubSequence(CANCEL, error(10028, "too_many_requests"), result("{}"));

    transport.callPrivate(CANCEL, Map.of(), Map.class, ReplaySafety.IDEMPOTENT_CANCELLATION);

    assertEquals(2, count(CANCEL));
  }

  @Test
  void placementRejectedWithTooManyRequestsIsNeverReplayed() throws Exception {
    stubSequence(AUTH, result(TOKEN_RESULT));
    stubSequence(BUY, error(10028, "too_many_requests"), result("{}"));

    RateLimitTerminatedException failure =
        assertThrows(
            RateLimitTerminatedException.class,
            () -> transport.callPrivate(BUY, Map.of(), Map.class, ReplaySafety.PLACEMENT));

    assertEquals(Reason.REMOTE_PRESSURE_EXHAUSTED, failure.getReason());
    assertEquals(Dispatch.REJECTED, failure.getDispatch());
    assertInstanceOf(CoinbaseDerivativesException.class, failure.getCause());
    assertEquals(
        RetryClassification.RATE_CREDIT,
        ((CoinbaseDerivativesException) failure.getCause()).getRetryClassification());
    assertEquals(1, count(BUY));
    assertEquals(0, context.diagnostics().getRetries());
  }

  @Test
  void placementRejectedWithHttp429IsNeverReplayed() throws Exception {
    stubSequence(AUTH, result(TOKEN_RESULT));
    stubSequence(BUY, aResponse().withStatus(429), result("{}"));

    RateLimitTerminatedException failure =
        assertThrows(
            RateLimitTerminatedException.class,
            () -> transport.callPrivate(BUY, Map.of(), Map.class, ReplaySafety.PLACEMENT));

    assertEquals(Dispatch.REJECTED, failure.getDispatch());
    assertEquals(1, count(BUY));
  }

  @Test
  void jwtIsMintedAfterAdmissionAndFreshForEveryAuthenticationAttempt() throws Exception {
    stubSequence(AUTH, error(10028, "too_many_requests"), result(TOKEN_RESULT));
    stubSequence(ACCOUNT_SUMMARY, result("{}"));

    transport.callPrivate(ACCOUNT_SUMMARY, Map.of(), Map.class, ReplaySafety.READ);

    assertEquals(2, mintedJwts.size());
    assertNotEquals(mintedJwts.get(0), mintedJwts.get(1));
    List<String> sent = new ArrayList<>();
    for (LoggedRequest request : server.findAll(authRequests())) {
      sent.add(json.readTree(request.getBodyAsString()).get("params").get("token").asText());
    }
    assertEquals(mintedJwts, sent);
    assertEquals(1, context.diagnostics().getRetries());
  }

  @Test
  void nothingIsMintedOrSentWhenAdmissionTerminates() {
    context.close();

    RateLimitTerminatedException failure =
        assertThrows(
            RateLimitTerminatedException.class,
            () -> transport.callPrivate(ACCOUNT_SUMMARY, Map.of(), Map.class, ReplaySafety.READ));

    assertEquals(Reason.SHUTDOWN, failure.getReason());
    assertEquals(Dispatch.NOT_SENT, failure.getDispatch());
    assertTrue(mintedJwts.isEmpty());
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void unknownMethodIsRejectedBeforeAnythingIsSent() {
    RateLimitTerminatedException failure =
        assertThrows(
            RateLimitTerminatedException.class,
            () -> transport.callPublic("public/not_declared", Map.of(), Map.class));

    assertEquals(Reason.UNCLASSIFIED_OPERATION, failure.getReason());
    assertEquals(Dispatch.NOT_SENT, failure.getDispatch());
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void defaultSpecificationEnablesTheRateLimiterWithTheDerivativesPolicy() {
    ExchangeSpecification specification =
        new CoinbaseDerivativesExchange().getDefaultExchangeSpecification();

    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertEquals(
        CoinbaseDerivativesRateLimitPolicy.NAMESPACE,
        specification.getResilience().getRateLimitPolicy().getNamespace());
  }

  @Test
  void exchangeWithDefaultSpecificationRejectsUnknownMethodsBeforeSend() {
    CoinbaseDerivativesExchange exchange = exchange(true);

    assertThrows(
        RateLimitTerminatedException.class,
        () -> exchange.getJsonRpcTransport().callPublic("public/not_declared", Map.of(), Map.class));
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void explicitlyDisabledRateLimiterLeavesTheTransportUnmetered() throws Exception {
    stubSequence("public/not_declared", result("{}"));
    CoinbaseDerivativesExchange exchange = exchange(false);

    Map<?, ?> result =
        exchange.getJsonRpcTransport().callPublic("public/not_declared", Map.of(), Map.class);

    assertEquals(Map.of(), result);
    assertEquals(1, count("public/not_declared"));
  }

  private CoinbaseDerivativesExchange exchange(boolean rateLimiterEnabled) {
    CoinbaseDerivativesExchange exchange = new CoinbaseDerivativesExchange();
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setSslUri(server.baseUrl() + "/");
    specification.setShouldLoadRemoteMetaData(false);
    specification.getResilience().setRateLimiterEnabled(rateLimiterEnabled);
    exchange.applySpecification(specification);
    return exchange;
  }

  private RateLimitOperation classify(String method, boolean authenticated) {
    RateLimitOperation operation = policy.classify(new RateLimitRequest(method, authenticated));
    assertNotNull(operation, method);
    assertEquals(method, operation.getName());
    return operation;
  }

  private int count(String method) {
    return server
        .findAll(
            postRequestedFor(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo(method))))
        .size();
  }

  private RequestPatternBuilder authRequests() {
    return postRequestedFor(urlEqualTo("/"))
        .withRequestBody(matchingJsonPath("$.method", equalTo(AUTH)));
  }

  private static ResponseDefinitionBuilder result(String resultJson) {
    return aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(
            "{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":"
                + resultJson
                + "}");
  }

  private static ResponseDefinitionBuilder error(int code, String message) {
    return aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(
            "{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"error\":{\"code\":"
                + code
                + ",\"message\":\""
                + message
                + "\"}}");
  }

  /** Serves the responses in order for one JSON-RPC method; the last one repeats. */
  private void stubSequence(String method, ResponseDefinitionBuilder... responses) {
    String scenario = "scenario-" + method;
    for (int i = 0; i < responses.length; i++) {
      String state = i == 0 ? Scenario.STARTED : "step-" + i;
      var mapping =
          post(urlEqualTo("/"))
              .withRequestBody(matchingJsonPath("$.method", equalTo(method)))
              .inScenario(scenario)
              .whenScenarioStateIs(state)
              .willReturn(responses[i]);
      if (i < responses.length - 1) {
        mapping = mapping.willSetStateTo("step-" + (i + 1));
      }
      server.stubFor(mapping);
    }
  }
}
