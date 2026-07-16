package org.knowm.xchange.coinbasederivatives;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasederivatives.client.ReplaySafety;

class CoinbaseDerivativesExchangeTest {
  @Test
  void defaultSpecificationExposesGatewayEndpointsAndSafeCancelDefault() {
    ExchangeSpecification specification =
        new CoinbaseDerivativesExchange().getDefaultExchangeSpecification();

    assertEquals(CoinbaseDerivativesExchange.HTTP_URI, specification.getSslUri());
    assertEquals(
        CoinbaseDerivativesExchange.WEBSOCKET_URI, specification.getOverrideWebsocketApiUri());
    assertFalse(
        (Boolean)
            specification.getExchangeSpecificParametersItem(
                CoinbaseDerivativesExchange.CANCEL_ON_DISCONNECT));
  }

  @Test
  void privateCallAuthenticatesWithFreshJwtInTokenParameter() throws Exception {
    WireMockServer server = new WireMockServer(options().dynamicPort());
    server.start();
    try {
      server.stubFor(
          post(urlEqualTo("/"))
              .withRequestBody(matchingJsonPath("$.method", equalTo("public/auth")))
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
                              + "\"access_token\":\"access\",\"token_type\":\"bearer\","
                              + "\"expires_in\":900,\"scope\":\"trade\"}}")));
      server.stubFor(
          post(urlEqualTo("/"))
              .withRequestBody(matchingJsonPath("$.method", equalTo("private/read")))
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}")));
      CoinbaseDerivativesExchange exchange = new CoinbaseDerivativesExchange();
      ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
      specification.setSslUri(server.baseUrl() + "/");
      specification.setApiKey("organizations/test/apiKeys/key");
      specification.setSecretKey(TestKeys.newEcPrivateKeyPem());
      specification.setShouldLoadRemoteMetaData(false);
      exchange.applySpecification(specification);

      exchange
          .getJsonRpcTransport()
          .callPrivate("private/read", Map.of(), Map.class, ReplaySafety.READ);

      server.verify(
          postRequestedFor(urlEqualTo("/"))
              .withRequestBody(matchingJsonPath("$.params.grant_type", equalTo("coinbase_cdp")))
              .withRequestBody(matchingJsonPath("$.params.token")));
    } finally {
      server.stop();
    }
  }
}
