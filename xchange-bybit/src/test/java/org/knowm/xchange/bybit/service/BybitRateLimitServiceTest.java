package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Test;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.BybitResilience;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.trade.BybitOrderResponse;
import org.knowm.xchange.bybit.dto.trade.BybitOrderType;
import org.knowm.xchange.bybit.dto.trade.BybitPlaceOrderPayload;
import org.knowm.xchange.bybit.dto.trade.BybitSide;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetail;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetails;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;

/**
 * Service-level proof that the xchange-core boundary is the only rate-admission owner of the REST
 * path: one admission per wire attempt, 429 feedback handled by the core, no resilience4j limiter
 * touched.
 */
public class BybitRateLimitServiceTest extends BaseWiremockTest {

  private static final String ORDER_REALTIME = "/v5/order/realtime";
  private static final String ORDER_CREATE = "/v5/order/create";

  private BybitExchange exchange;

  @After
  public void closeRateLimitContext() {
    if (exchange == null) {
      return;
    }
    RateLimitContext context = exchange.getExchangeSpecification().getResilience().getRateLimitContext();
    if (context != null) {
      context.close();
    }
  }

  private BybitTradeServiceRaw trade() throws IOException {
    exchange = createExchange();
    return (BybitTradeServiceRaw) exchange.getTradeService();
  }

  private RateLimitContext context() {
    return exchange.getExchangeSpecification().getResilience().getRateLimitContext();
  }

  @Test
  public void readPerformsOneAdmissionPerWireAttempt() throws IOException {
    initGetStub(ORDER_REALTIME, "/getOrderDetailsLinear.json5");
    BybitTradeServiceRaw trade = trade();

    BybitResult<BybitOrderDetails<BybitOrderDetail>> result =
        trade.getBybitOrder(BybitCategory.LINEAR, null, "fd4300ae-7847-404e-b947-b46980a4d140");

    assertEquals(1, result.getResult().getList().size());
    wireMockRule.verify(1, getRequestedFor(urlPathEqualTo(ORDER_REALTIME)));
    assertEquals("one admission for one wire attempt", 1, context().diagnostics().getAdmissions());
    assertEquals(0, context().diagnostics().getRetries());
  }

  @Test
  public void rejectedReadIsReplayedByTheCoreWithFreshAdmission() throws IOException {
    String body =
        IOUtils.resourceToString("/getOrderDetailsLinear.json5", StandardCharsets.UTF_8);
    wireMockRule.stubFor(
        get(urlPathEqualTo(ORDER_REALTIME))
            .inScenario("read")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("recovered")
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Retry-After", "1")
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"retCode\":10006,\"retMsg\":\"Too many visits!\"}")));
    wireMockRule.stubFor(
        get(urlPathEqualTo(ORDER_REALTIME))
            .inScenario("read")
            .whenScenarioStateIs("recovered")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
    BybitTradeServiceRaw trade = trade();

    BybitResult<BybitOrderDetails<BybitOrderDetail>> result =
        trade.getBybitOrder(BybitCategory.LINEAR, null, "fd4300ae-7847-404e-b947-b46980a4d140");

    assertEquals(1, result.getResult().getList().size());
    wireMockRule.verify(2, getRequestedFor(urlPathEqualTo(ORDER_REALTIME)));
    assertEquals(
        "every wire attempt was admitted exactly once by the core",
        2,
        context().diagnostics().getAdmissions());
    assertEquals(1, context().diagnostics().getRetries());
    assertEquals(1, context().diagnostics().getRatePressureEvents());
  }

  @Test
  public void rejectedOrderPlacementIsNeverBlindlyReplayed() throws IOException {
    wireMockRule.stubFor(
        post(urlPathEqualTo(ORDER_CREATE))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Retry-After", "1")
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"retCode\":10006,\"retMsg\":\"Too many visits!\"}")));
    BybitTradeServiceRaw trade = trade();

    try {
      trade.placeOrder(
          new BybitPlaceOrderPayload(
              BybitCategory.LINEAR,
              "ETHUSDT",
              BybitSide.BUY,
              BybitOrderType.MARKET,
              new BigDecimal("0.10"),
              "link-rate-001"),
          BybitCategory.LINEAR);
      fail("a rejected placement must surface");
    } catch (RateLimitTerminatedException terminated) {
      assertEquals(RateLimitTerminatedException.Dispatch.REJECTED, terminated.getDispatch());
      assertEquals(
          RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED, terminated.getReason());
    }

    wireMockRule.verify(1, postRequestedFor(urlPathEqualTo(ORDER_CREATE)));
    assertEquals(1, context().diagnostics().getAdmissions());
    assertEquals(0, context().diagnostics().getRetries());
  }

  @Test
  public void readRejectedInAnHttp200BodyIsReplayedWithFreshAdmission() throws IOException {
    String body =
        IOUtils.resourceToString("/getOrderDetailsLinear.json5", StandardCharsets.UTF_8);
    wireMockRule.stubFor(
        get(urlPathEqualTo(ORDER_REALTIME))
            .inScenario("read-body")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("recovered")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"retCode\":10006,\"retMsg\":\"Too many visits!\"}")));
    wireMockRule.stubFor(
        get(urlPathEqualTo(ORDER_REALTIME))
            .inScenario("read-body")
            .whenScenarioStateIs("recovered")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
    BybitTradeServiceRaw trade = trade();

    BybitResult<BybitOrderDetails<BybitOrderDetail>> result =
        trade.getBybitOrder(BybitCategory.LINEAR, null, "fd4300ae-7847-404e-b947-b46980a4d140");

    assertEquals(1, result.getResult().getList().size());
    wireMockRule.verify(2, getRequestedFor(urlPathEqualTo(ORDER_REALTIME)));
    assertEquals(
        "every wire attempt was admitted exactly once by the core",
        2,
        context().diagnostics().getAdmissions());
    assertEquals(1, context().diagnostics().getRetries());
    assertEquals(1, context().diagnostics().getRatePressureEvents());
  }

  @Test
  public void placementRejectedInAnHttp200BodyIsSentOnceAndStartsCooldown() throws IOException {
    wireMockRule.stubFor(
        post(urlPathEqualTo(ORDER_CREATE))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"retCode\":10006,\"retMsg\":\"Too many visits!\"}")));
    BybitTradeServiceRaw trade = trade();

    try {
      trade.placeOrder(
          new BybitPlaceOrderPayload(
              BybitCategory.LINEAR,
              "ETHUSDT",
              BybitSide.BUY,
              BybitOrderType.MARKET,
              new BigDecimal("0.10"),
              "link-rate-002"),
          BybitCategory.LINEAR);
      fail("a body-rejected placement must surface");
    } catch (RateLimitTerminatedException terminated) {
      assertEquals(RateLimitTerminatedException.Dispatch.REJECTED, terminated.getDispatch());
      assertEquals(
          RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED, terminated.getReason());
    }

    wireMockRule.verify(1, postRequestedFor(urlPathEqualTo(ORDER_CREATE)));
    assertEquals(1, context().diagnostics().getAdmissions());
    assertEquals(0, context().diagnostics().getRetries());
    assertEquals(1, context().diagnostics().getRatePressureEvents());
  }

  @Test
  public void restPathNeverTouchesTheLegacyResilience4jLimiters() throws IOException {
    initGetStub(ORDER_REALTIME, "/getOrderDetailsLinear.json5");
    wireMockRule.stubFor(
        post(urlPathEqualTo(ORDER_CREATE))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"retCode\":0,\"retMsg\":\"OK\",\"result\":{\"orderId\":\"1\","
                            + "\"orderLinkId\":\"link-rate-002\"},\"retExtInfo\":{},\"time\":1}")));
    BybitTradeServiceRaw trade = trade();

    trade.getBybitOrder(BybitCategory.LINEAR, null, "fd4300ae-7847-404e-b947-b46980a4d140");
    BybitResult<BybitOrderResponse> placed =
        trade.placeOrder(
            new BybitPlaceOrderPayload(
                BybitCategory.LINEAR,
                "ETHUSDT",
                BybitSide.BUY,
                BybitOrderType.MARKET,
                new BigDecimal("0.10"),
                "link-rate-002"),
            BybitCategory.LINEAR);
    assertTrue(placed.isSuccess());

    RateLimiter legacyCreate =
        exchange
            .getResilienceRegistries()
            .rateLimiters()
            .rateLimiter(BybitResilience.ORDER_CREATE_LINEAR_AND_INVERSE_RATE_LIMITER);
    assertEquals(
        "the resilience4j create limiter was not consumed by the REST call",
        legacyCreate.getRateLimiterConfig().getLimitForPeriod(),
        legacyCreate.getMetrics().getAvailablePermissions());
    assertTrue(
        "legacy global limiter no longer registered",
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().stream()
            .noneMatch(limiter -> "global".equals(limiter.getName())));
    assertEquals(2, context().diagnostics().getAdmissions());
  }
}
