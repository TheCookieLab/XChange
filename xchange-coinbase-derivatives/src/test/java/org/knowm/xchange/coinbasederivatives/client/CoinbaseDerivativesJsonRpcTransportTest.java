package org.knowm.xchange.coinbasederivatives.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbasederivatives.TestKeys;
import org.knowm.xchange.coinbasederivatives.auth.AccessToken;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesAccessTokenProvider;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesJwtGenerator;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesTicker;

class CoinbaseDerivativesJsonRpcTransportTest {
  private WireMockServer server;

  @BeforeEach
  void startServer() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop();
  }

  @Test
  void correlatesEnvelopeAndPreservesExactDecimals() throws Exception {
    server.stubFor(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
                            + "\"instrument_name\":\"BTC_USDC-PERPETUAL\","
                            + "\"timestamp\":1,\"last_price\":1e-8,"
                            + "\"best_bid_price\":99999.123456789012345678}}")));

    CoinbaseDerivativesTicker ticker =
        transport()
            .callPublic(
                "public/ticker",
                Map.of("instrument_name", "BTC_USDC-PERPETUAL"),
                CoinbaseDerivativesTicker.class);

    assertEquals(new BigDecimal("1E-8"), ticker.lastPrice());
    assertEquals(new BigDecimal("99999.123456789012345678"), ticker.bestBidPrice());
    server.verify(
        postRequestedFor(urlEqualTo("/"))
            .withRequestBody(
                equalToJson(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"public/ticker\","
                        + "\"params\":{\"instrument_name\":\"BTC_USDC-PERPETUAL\"}}")));
  }

  @Test
  void permanentProtocolFailureIsNotRetried() {
    server.stubFor(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"jsonrpc\":\"2.0\",\"id\":999,\"result\":{}}")));

    CoinbaseDerivativesException failure =
        assertThrows(
            CoinbaseDerivativesException.class,
            () ->
                transport().callPublic("public/ticker", Map.of(), CoinbaseDerivativesTicker.class));

    assertEquals(RetryClassification.PERMANENT, failure.getRetryClassification());
    server.verify(1, postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void retriesOnlyTransientPublicTransportFailures() throws Exception {
    server.stubFor(
        post(urlEqualTo("/"))
            .inScenario("transient")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("recovered")
            .willReturn(aResponse().withStatus(503)));
    server.stubFor(
        post(urlEqualTo("/"))
            .inScenario("transient")
            .whenScenarioStateIs("recovered")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"timestamp\":1}}")));

    transport().callPublic("public/ticker", Map.of(), CoinbaseDerivativesTicker.class);

    server.verify(2, postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void placementTransportFailureIsAmbiguousAndNeverRetried() throws Exception {
    server.stubFor(post(urlEqualTo("/")).willReturn(aResponse().withStatus(503)));
    CoinbaseDerivativesJsonRpcTransport transport = authenticatedTransport();

    CoinbaseDerivativesException failure =
        assertThrows(
            CoinbaseDerivativesException.class,
            () ->
                transport.callPrivate(
                    "private/buy",
                    Map.of("label", "duplicate"),
                    Map.class,
                    ReplaySafety.PLACEMENT));

    assertEquals(RetryClassification.AMBIGUOUS, failure.getRetryClassification());
    server.verify(1, postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void authorizationFailureRefreshesOnceForReplaySafeRead() throws Exception {
    server.stubFor(
        post(urlEqualTo("/"))
            .inScenario("auth")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("authenticated")
            .willReturn(aResponse().withStatus(401).withBody("access_token=secret")));
    server.stubFor(
        post(urlEqualTo("/"))
            .inScenario("auth")
            .whenScenarioStateIs("authenticated")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}")));

    Map<?, ?> result =
        authenticatedTransport()
            .callPrivate("private/read", Map.of(), Map.class, ReplaySafety.READ);

    assertEquals(Map.of(), result);
    server.verify(2, postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void redactionRemovesBearerJwtAndTokenFields() {
    String sanitized =
        CoinbaseDerivativesRedactor.sanitize(
            "Authorization: Bearer eyJabc.def.sig access_token=topsecret client_secret=hidden");

    assertFalse(sanitized.contains("eyJabc.def.sig"));
    assertFalse(sanitized.contains("topsecret"));
    assertFalse(sanitized.contains("hidden"));
  }

  private CoinbaseDerivativesJsonRpcTransport transport() {
    return new CoinbaseDerivativesJsonRpcTransport(URI.create(server.baseUrl() + "/"));
  }

  private CoinbaseDerivativesJsonRpcTransport authenticatedTransport() throws Exception {
    CoinbaseDerivativesJsonRpcTransport transport = transport();
    CoinbaseDerivativesAccessTokenProvider provider =
        new CoinbaseDerivativesAccessTokenProvider(
            new CoinbaseDerivativesJwtGenerator("key", TestKeys.newEcPrivateKeyPem()),
            jwt -> new AccessToken("token-" + System.nanoTime(), "bearer", 900, "trade"));
    transport.setAccessTokenProvider(provider);
    return transport;
  }
}
