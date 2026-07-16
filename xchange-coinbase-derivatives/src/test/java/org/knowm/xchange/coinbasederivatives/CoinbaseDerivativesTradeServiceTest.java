package org.knowm.xchange.coinbasederivatives;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasederivatives.auth.AccessToken;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesAccessTokenProvider;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesJwtGenerator;
import org.knowm.xchange.coinbasederivatives.client.CoinbaseDerivativesJsonRpcTransport;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.coinbasederivatives.service.CoinbaseDerivativesMarketDataService;
import org.knowm.xchange.coinbasederivatives.service.CoinbaseDerivativesTradeService;
import org.knowm.xchange.currency.CurrencyPair;

class CoinbaseDerivativesTradeServiceTest {
  private WireMockServer server;
  private CoinbaseDerivativesTradeService service;
  private CoinbaseDerivativesMarketDataService marketDataService;

  @BeforeEach
  void setUp() throws Exception {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    CoinbaseDerivativesJsonRpcTransport transport =
        new CoinbaseDerivativesJsonRpcTransport(URI.create(server.baseUrl() + "/"));
    transport.setAccessTokenProvider(
        new CoinbaseDerivativesAccessTokenProvider(
            new CoinbaseDerivativesJwtGenerator("key", TestKeys.newEcPrivateKeyPem()),
            jwt -> new AccessToken("access", "bearer", 900, "trade")));
    CoinbaseDerivativesExchange exchange = new CoinbaseDerivativesExchange(transport);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);
    service = (CoinbaseDerivativesTradeService) exchange.getTradeService();
    marketDataService = (CoinbaseDerivativesMarketDataService) exchange.getMarketDataService();
    CoinbaseDerivativesAdapters.registerInstrument(
        new CoinbaseDerivativesInstrument(
            "BTC_USDC-PERPETUAL",
            "future",
            "BTC",
            "USDC",
            "USDC",
            true,
            new BigDecimal("0.01"),
            new BigDecimal("0.0001"),
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void rawStopLimitPlacementPreservesEveryIdentityAndAcceptedField() throws Exception {
    server.stubFor(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"order\":{"
                            + "\"order_id\":\"initial-1\",\"primary_order_id\":\"parent-1\","
                            + "\"oto_order_ids\":[\"triggered-1\"],"
                            + "\"instrument_name\":\"BTC_USDC-PERPETUAL\","
                            + "\"direction\":\"buy\",\"order_type\":\"stop_limit\","
                            + "\"amount\":0.0001,\"price\":100000.25,\"reduce_only\":true,"
                            + "\"label\":\"duplicate-label\",\"order_state\":\"untriggered\"},"
                            + "\"trades\":[]}}")));

    CoinbaseDerivativesPlacementResult result =
        service.placeOrder(
            "buy",
            "BTC_USDC-PERPETUAL",
            new BigDecimal("0.0001"),
            "stop_limit",
            "duplicate-label",
            new BigDecimal("100000.25"),
            true,
            new BigDecimal("99999.75"));

    assertEquals("initial-1", result.primaryOrderId());
    assertEquals(List.of("parent-1", "triggered-1"), result.relatedOrderIds());
    assertEquals(1L, result.requestCorrelationId());
    assertTrue(result.reduceOnly());
    assertEquals("duplicate-label", result.label());
    server.verify(
        postRequestedFor(urlEqualTo("/"))
            .withRequestBody(matchingJsonPath("$.method", equalTo("private/buy")))
            .withRequestBody(matchingJsonPath("$.params.type", equalTo("stop_limit")))
            .withRequestBody(matchingJsonPath("$.params.trigger", equalTo("last_price")))
            .withRequestBody(matchingJsonPath("$.params.reduce_only", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.params.label", equalTo("duplicate-label"))));
  }

  @Test
  void currencyPairTickerOverloadResolvesDiscoveredPerpetual() throws Exception {
    server.stubFor(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
                            + "\"instrument_name\":\"BTC_USDC-PERPETUAL\","
                            + "\"timestamp\":1,\"last_price\":100000.125}}")));

    var ticker = marketDataService.getTicker(new CurrencyPair("BTC", "USDC"));

    assertEquals(new BigDecimal("100000.125"), ticker.getLast());
    server.verify(
        postRequestedFor(urlEqualTo("/"))
            .withRequestBody(
                matchingJsonPath("$.params.instrument_name", equalTo("BTC_USDC-PERPETUAL"))));
  }
}
