package org.knowm.xchange.kucoin;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitDiagnostics;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.kucoin.dto.request.OrderCreateApiRequest;
import org.knowm.xchange.kucoin.dto.response.SymbolResponse;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderPlaceRequest;
import org.knowm.xchange.kucoin.uta.service.UtaApiException;

/**
 * Public-service-level proof that the universal core limiter is the single rate owner of the KuCoin
 * wire path: every wire attempt is admitted once, a read rejected with HTTP 429 is replayed with a
 * fresh admission, an order placement is never blindly replayed, and no resilience4j rate limiter
 * participates. Runs against a dynamic-port loopback WireMock server.
 */
class KucoinRateLimitServiceTest {

  private static final String SYMBOLS_PATH = "/api/v2/symbols";
  private static final String CLASSIC_ORDER_PATH = "/api/v1/hf/orders";
  private static final String UTA_PLACE_PATH = "/api/ua/v1/unified/order/place";
  private static final String UTA_DETAIL_PATH = "/api/ua/v1/unified/order/detail";

  private static final String SYMBOLS_BODY =
      "{\"code\":\"200000\",\"data\":[{\"symbol\":\"BTC-USDT\",\"name\":\"BTC-USDT\","
          + "\"baseCurrency\":\"BTC\",\"quoteCurrency\":\"USDT\",\"enableTrading\":true}]}";

  private static final String RATE_LIMITED_BODY =
      "{\"code\":\"429000\",\"msg\":\"Too Many Requests\"}";

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private ExchangeSpecification specification;

  @AfterEach
  void closeLimiter() {
    if (specification != null) {
      RateLimitContext context = specification.getResilience().getRateLimitContext();
      if (context != null) {
        context.close();
      }
    }
  }

  private KucoinExchange createExchange(boolean uta) {
    KucoinExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(KucoinExchange.class);
    specification = exchange.getDefaultExchangeSpecification();
    specification.setHost("localhost");
    specification.setSslUri("http://localhost:" + wireMock.getPort() + "/");
    specification.setPort(wireMock.getPort());
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");
    specification.setExchangeSpecificParametersItem("passphrase", "test-passphrase");
    if (uta) {
      specification.setExchangeSpecificParametersItem(
          KucoinExchange.API_MODE_PARAMETER, KucoinApiMode.UTA);
    }
    exchange.applySpecification(specification);
    return exchange;
  }

  private RateLimitDiagnostics diagnostics() {
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    assertNotNull(context, "the exchange must own a universal rate limit context");
    return context.diagnostics();
  }

  private static void stubRateLimitedThenOk(String path, String okBody) {
    wireMock.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(path))
            .inScenario("rate")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("gw-ratelimit-limit", "2000")
                    .withHeader("gw-ratelimit-remaining", "0")
                    .withHeader("gw-ratelimit-reset", "5")
                    .withBody(RATE_LIMITED_BODY))
            .willSetStateTo("recovered"));
    wireMock.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(path))
            .inScenario("rate")
            .whenScenarioStateIs("recovered")
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(okBody)));
  }

  @Test
  void defaultSpecificationEnablesTheCoreLimiterAndNoResilience4jLimiterExists() throws Exception {
    KucoinExchange exchange = createExchange(false);

    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertEquals(
        KucoinRateLimitPolicy.defaultPolicy(), specification.getResilience().getRateLimitPolicy());

    wireMock.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(SYMBOLS_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SYMBOLS_BODY)));

    List<SymbolResponse> symbols = exchange.getMarketDataService().getKucoinSymbolsV2();

    assertEquals(1, symbols.size());
    assertEquals(1, diagnostics().getAdmissions(), "one admission per wire attempt");
    assertEquals(0, diagnostics().getRetries());
    assertTrue(
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty(),
        "no resilience4j rate limiter may be registered on the KuCoin path");
  }

  @Test
  void classicReadRejectedWith429IsReplayedWithFreshAdmission() throws Exception {
    KucoinExchange exchange = createExchange(false);
    stubRateLimitedThenOk(SYMBOLS_PATH, SYMBOLS_BODY);

    List<SymbolResponse> symbols = exchange.getMarketDataService().getKucoinSymbolsV2();

    assertEquals(1, symbols.size());
    wireMock.verify(2, WireMock.getRequestedFor(WireMock.urlPathEqualTo(SYMBOLS_PATH)));
    assertEquals(2, diagnostics().getAdmissions(), "each wire attempt takes its own admission");
    assertEquals(1, diagnostics().getRetries(), "the core owns the single replay");
    assertTrue(exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty());
  }

  @Test
  void classicOrderPlacementRejectedWith429IsNotReplayed() {
    KucoinExchange exchange = createExchange(false);
    wireMock.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(CLASSIC_ORDER_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("gw-ratelimit-reset", "5")
                    .withBody(RATE_LIMITED_BODY)));

    OrderCreateApiRequest request =
        OrderCreateApiRequest.builder()
            .clientOid("ord-1")
            .symbol("BTC-USDT")
            .side("buy")
            .price("65000")
            .size("0.001")
            .build();

    RateLimitTerminatedException e =
        assertThrows(
            RateLimitTerminatedException.class,
            () -> exchange.getTradeService().kucoinCreateOrder(request));

    assertEquals(RateLimitTerminatedException.Dispatch.REJECTED, e.getDispatch());
    wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(CLASSIC_ORDER_PATH)));
    assertEquals(1, diagnostics().getAdmissions());
    assertEquals(0, diagnostics().getRetries());
  }

  @Test
  void utaReadRejectedWith429IsReplayedByTheCoreOnly() throws Exception {
    KucoinExchange exchange = createExchange(true);
    String instruments =
        "{\"code\":\"200000\",\"data\":{\"tradeType\":\"SPOT\",\"list\":[]}}";
    stubRateLimitedThenOk("/api/ua/v1/market/instrument", instruments);

    exchange.getUtaMarketDataService().getUtaInstruments("SPOT");

    wireMock.verify(
        2, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/ua/v1/market/instrument")));
    assertEquals(2, diagnostics().getAdmissions());
    assertEquals(1, diagnostics().getRetries());
    assertTrue(exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty());
  }

  @Test
  void utaPlacementRejectedWith429IsNeitherReplayedNorReconciledAsUnknown() {
    KucoinExchange exchange = createExchange(true);
    wireMock.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(UTA_PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("gw-ratelimit-reset", "5")
                    .withBody(RATE_LIMITED_BODY)));

    UtaOrderPlaceRequest request =
        UtaOrderPlaceRequest.builder()
            .tradeType("SPOT")
            .symbol("BTC-USDT")
            .clientOid("ord-123")
            .side("BUY")
            .orderType("LIMIT")
            .size("0.001")
            .sizeUnit("BASECCY")
            .price("65000")
            .build();

    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () -> exchange.getUtaTradeService().placeOrderSafe(request, CurrencyPair.BTC_USDT));

    assertTrue(e.getCause() instanceof RateLimitTerminatedException, String.valueOf(e.getCause()));
    assertEquals(
        RateLimitTerminatedException.Dispatch.REJECTED,
        ((RateLimitTerminatedException) e.getCause()).getDispatch());
    assertNotEquals(UtaApiException.RetryClassification.UNKNOWN_OUTCOME, e.getRetryClassification());
    wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(UTA_PLACE_PATH)));
    wireMock.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(UTA_DETAIL_PATH)));
    assertEquals(1, diagnostics().getAdmissions());
    assertEquals(0, diagnostics().getRetries());
  }
}
