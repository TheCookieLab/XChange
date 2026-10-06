package org.knowm.xchange.blockchain.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.knowm.xchange.blockchain.service.utils.BlockchainConstants.APPLICATION;
import static org.knowm.xchange.blockchain.service.utils.BlockchainConstants.CONTENT_TYPE;
import static org.knowm.xchange.blockchain.service.utils.BlockchainConstants.ORDERS_JSON;
import static org.knowm.xchange.blockchain.service.utils.BlockchainConstants.URL_ORDERS;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.blockchain.BlockchainExchange;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitDiagnostics;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.service.trade.TradeService;

/**
 * Service-level proof that the core rate limiter is the only admission and rejection owner of the
 * Blockchain.com REST path: one admission per wire attempt, rejections replayed only for safe
 * operations with a fresh admission, and no resilience4j rate limiter left on the path.
 */
public class BlockchainRateLimitServiceTest extends BlockchainBaseTest {

  private static final String SCENARIO = "rate-rejection";

  private BlockchainExchange exchange;
  private TradeService service;
  private RateLimitContext context;

  @Before
  public void init() {
    wireMockRule.resetAll();
    exchange =
        createExchange(
            policy -> policy.withFallbackBackoff(Duration.ofMillis(5), Duration.ofMillis(20), 0.0));
    service = exchange.getTradeService();
    context = exchange.getExchangeSpecification().getResilience().getRateLimitContext();
  }

  @After
  public void close() {
    context.close();
  }

  @Test(timeout = 5000)
  public void everyWireAttemptIsAdmittedExactlyOnce() throws Exception {
    wireMockRule.stubFor(
        get(urlPathEqualTo(URL_ORDERS))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, APPLICATION)
                    .withBodyFile(ORDERS_JSON)));

    for (int i = 0; i < 3; i++) {
      assertThat(service.getOpenOrders().getAllOpenOrders()).isNotEmpty();
    }

    wireMockRule.verify(3, getRequestedFor(urlPathEqualTo(URL_ORDERS)));
    RateLimitDiagnostics diagnostics = context.diagnostics();
    assertThat(diagnostics.getAdmissions()).isEqualTo(3);
    assertThat(diagnostics.getRetries()).isZero();
    assertThat(diagnostics.getRatePressureEvents()).isZero();
    assertThat(exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters()).isEmpty();
  }

  @Test(timeout = 5000)
  public void rejectedReadIsReplayedWithFreshAdmission() throws Exception {
    wireMockRule.stubFor(
        get(urlPathEqualTo(URL_ORDERS))
            .inScenario(SCENARIO)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(429))
            .willSetStateTo("recovered"));
    wireMockRule.stubFor(
        get(urlPathEqualTo(URL_ORDERS))
            .inScenario(SCENARIO)
            .whenScenarioStateIs("recovered")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, APPLICATION)
                    .withBodyFile(ORDERS_JSON)));

    assertThat(service.getOpenOrders().getAllOpenOrders()).isNotEmpty();

    wireMockRule.verify(2, getRequestedFor(urlPathEqualTo(URL_ORDERS)));
    RateLimitDiagnostics diagnostics = context.diagnostics();
    assertThat(diagnostics.getAdmissions()).as("one admission per wire attempt").isEqualTo(2);
    assertThat(diagnostics.getRetries()).isEqualTo(1);
    assertThat(diagnostics.getRatePressureEvents()).isEqualTo(1);
    assertThat(exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters()).isEmpty();
  }

  @Test(timeout = 5000)
  public void rejectedOrderPlacementIsSentOnceAndSurfaced() {
    wireMockRule.stubFor(post(urlPathEqualTo(URL_ORDERS)).willReturn(aResponse().withStatus(429)));

    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("45.0"))
            .limitPrice(new BigDecimal("0.23"))
            .build();
    Throwable failure = catchThrowable(() -> service.placeLimitOrder(order));

    assertThat(failure).isInstanceOf(RateLimitTerminatedException.class);
    RateLimitTerminatedException terminated = (RateLimitTerminatedException) failure;
    assertThat(terminated.getDispatch()).isEqualTo(RateLimitTerminatedException.Dispatch.REJECTED);
    assertThat(terminated.getReason())
        .isEqualTo(RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED);
    wireMockRule.verify(1, postRequestedFor(urlPathEqualTo(URL_ORDERS)));
    RateLimitDiagnostics diagnostics = context.diagnostics();
    assertThat(diagnostics.getAdmissions()).isEqualTo(1);
    assertThat(diagnostics.getRetries()).isZero();
  }
}
