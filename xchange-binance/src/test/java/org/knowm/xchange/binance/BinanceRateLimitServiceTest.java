package org.knowm.xchange.binance;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.dto.trade.BinanceNewOrder;
import org.knowm.xchange.binance.dto.trade.OrderSide;
import org.knowm.xchange.binance.dto.trade.OrderType;
import org.knowm.xchange.binance.dto.trade.TimeInForce;
import org.knowm.xchange.binance.service.BinanceTradeServiceRaw;
import org.knowm.xchange.client.ratelimit.RateLimitDiagnostics;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;

/**
 * Public-service-level proof that the core rate limiter is the single admission and feedback owner
 * of every Binance REST wire attempt.
 */
public class BinanceRateLimitServiceTest extends AbstractResilienceTest {

  private static final String DEPTH = "/api/v3/depth";
  private static final String ORDER = "/api/v3/order";

  @Test
  public void rejectedReadIsReplayedWithFreshAdmissionExactlyOncePerWireAttempt() throws Exception {
    BinanceExchange exchange = createExchangeWithRateLimiterEnabled();
    stubDepthRejectedOnceThenServed();

    OrderBook book = exchange.getMarketDataService().getOrderBook(CurrencyPair.BTC_USDT);

    assertThat(book.getBids()).isNotEmpty();
    verify(2, getRequestedFor(urlPathEqualTo(DEPTH)));
    RateLimitDiagnostics diagnostics = diagnostics(exchange);
    assertThat(diagnostics.getAdmissions()).isEqualTo(2L);
    assertThat(diagnostics.getRetries()).isEqualTo(1L);
    assertThat(diagnostics.getRatePressureEvents()).isGreaterThanOrEqualTo(1L);
  }

  @Test
  public void successfulReadIsAdmittedOnceWhenResilienceRetryIsEnabledToo() throws Exception {
    BinanceExchange exchange = createExchange(true, true);
    stubFor(
        get(urlPathEqualTo(DEPTH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("depth.json")));

    exchange.getMarketDataService().getOrderBook(CurrencyPair.BTC_USDT);

    verify(1, getRequestedFor(urlPathEqualTo(DEPTH)));
    assertThat(diagnostics(exchange).getAdmissions()).isEqualTo(1L);
    assertThat(diagnostics(exchange).getRetries()).isZero();
  }

  @Test
  public void rejectedReadIsNeverRetriedByTheModuleResilienceRetry() {
    BinanceExchange exchange = createExchange(true, true);
    stubFor(
        get(urlPathEqualTo(DEPTH))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")));

    Throwable failure =
        catchThrowable(() -> exchange.getMarketDataService().getOrderBook(CurrencyPair.BTC_USDT));

    assertThat(failure).isInstanceOf(RateLimitTerminatedException.class);
    // every wire attempt was admitted by the core exactly once; the module retry added none
    long admissions = diagnostics(exchange).getAdmissions();
    assertThat(admissions).isGreaterThanOrEqualTo(1L);
    verify((int) admissions, getRequestedFor(urlPathEqualTo(DEPTH)));
  }

  @Test
  public void rejectedOrderPlacementIsSentOnceAndSurfacedNeverReplayed() {
    BinanceExchange exchange = createExchangeWithRateLimiterEnabled();
    stubFor(
        post(urlPathEqualTo(ORDER))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")));
    BinanceTradeServiceRaw trade = (BinanceTradeServiceRaw) exchange.getTradeService();

    Throwable failure =
        catchThrowable(
            () ->
                trade.newOrder(
                    CurrencyPair.BTC_USDT,
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    TimeInForce.GTC,
                    new BigDecimal("0.001"),
                    null,
                    new BigDecimal("10000"),
                    null,
                    null,
                    null,
                    null,
                    BinanceNewOrder.NewOrderResponseType.ACK));

    assertThat(failure).isInstanceOf(RateLimitTerminatedException.class);
    assertThat(((RateLimitTerminatedException) failure).getDispatch())
        .isEqualTo(RateLimitTerminatedException.Dispatch.REJECTED);
    verify(1, postRequestedFor(urlPathEqualTo(ORDER)));
    assertThat(diagnostics(exchange).getAdmissions()).isEqualTo(1L);
    assertThat(diagnostics(exchange).getRetries()).isZero();
  }

  @Test
  public void defaultSpecificationsCarryThePolicyAndEnableTheLimiter() {
    ExchangeSpecification specification = new BinanceExchange().getDefaultExchangeSpecification();
    assertThat(specification.getResilience().isRateLimiterEnabled()).isTrue();
    assertThat(specification.getResilience().getRateLimitPolicy())
        .isSameAs(BinanceRateLimitPolicy.defaultPolicy());
  }

  private static RateLimitDiagnostics diagnostics(BinanceExchange exchange) {
    return exchange
        .getExchangeSpecification()
        .getResilience()
        .getRateLimitContext()
        .diagnostics();
  }

  private void stubDepthRejectedOnceThenServed() {
    stubFor(
        get(urlPathEqualTo(DEPTH))
            .inScenario("Rate rejection")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
            .willSetStateTo("Cooled down"));
    stubFor(
        get(urlPathEqualTo(DEPTH))
            .inScenario("Rate rejection")
            .whenScenarioStateIs("Cooled down")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("depth.json")));
  }
}
