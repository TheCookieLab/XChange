package org.knowm.xchange.bybit;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.matching.ContainsPattern;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.config.BybitEnvironment;
import org.knowm.xchange.bybit.service.BaseWiremockTest;
import org.knowm.xchange.bybit.service.BybitAccountService;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitDeadline;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;

class BybitExchangeTest extends BaseWiremockTest {

  @Test
  void symbolLoading() throws Exception {
    Exchange bybitExchange = createExchange();

    initGetStub(
        "/v5/market/instruments-info",
        "/getInstrumentSpot.json5",
        "category",
        new ContainsPattern("spot"));
    initGetStub(
        "/v5/market/instruments-info",
        "/getInstrumentLinear.json5",
        "category",
        new ContainsPattern("linear"));
    initGetStub(
        "/v5/market/instruments-info",
        "/getInstrumentInverse.json5",
        "category",
        new ContainsPattern("inverse"));
    initGetStub(
        "/v5/market/instruments-info",
        "/getInstrumentOption.json5",
        "category",
        new ContainsPattern("option"));
    initGetStub("/v5/account/fee-rate", "/getFeeRates.json5");

    ExchangeSpecification specification = bybitExchange.getExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(true);
    bybitExchange.applySpecification(specification);

    assertThat(bybitExchange.getExchangeMetaData().getInstruments()).hasSize(4);
  }

  @Test
  public void applySpecificationResolvesSslUriFromEnvironment() {
    assertSslUri(specWithRemoteMetaDataDisabled(), "https://api.bybit.com");

    ExchangeSpecification demo = specWithRemoteMetaDataDisabled();
    demo.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
    assertSslUri(demo, BybitEnvironment.DEMO.getRestBaseUrl());

    ExchangeSpecification testnet = specWithRemoteMetaDataDisabled();
    testnet.setExchangeSpecificParametersItem(BybitExchange.SPECIFIC_PARAM_TESTNET, true);
    assertSslUri(testnet, BybitEnvironment.TESTNET.getRestBaseUrl());
  }

  @Test
  public void applySpecificationRejectsConflictingEnvironments() {
    ExchangeSpecification spec = specWithRemoteMetaDataDisabled();
    spec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
    spec.setExchangeSpecificParametersItem(BybitExchange.SPECIFIC_PARAM_TESTNET, true);

    Throwable thrown = catchThrowable(() -> new BybitExchange().applySpecification(spec));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting Bybit environments");
  }

  @Test
  public void applySpecificationPreservesExplicitSslUri() {
    ExchangeSpecification spec = specWithRemoteMetaDataDisabled();
    spec.setSslUri("http://localhost:8080");
    spec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
    BybitExchange exchange = new BybitExchange();
    exchange.applySpecification(spec);
    assertThat(exchange.getExchangeSpecification().getSslUri()).isEqualTo("http://localhost:8080");
  }

  @Test
  void rateLimiterTest() throws Exception {
    RateLimitPolicy policy =
        BybitRateLimitPolicy.defaultPolicy()
            .withBudget(
                RateLimitBudget.rollingWindow(
                    BybitRateLimitPolicy.POSITION_SET_LEVERAGE,
                    ScopeKind.USER,
                    1,
                    Duration.ofHours(1)));
    BybitExchange bybitExchange = createExchange(policy);
    try (RateLimitContext context =
        bybitExchange.getExchangeSpecification().getResilience().getRateLimitContext()) {
      initPostStub("/v5/position/set-leverage", "/setLeverage.json5");
      BybitAccountService accountService = (BybitAccountService) bybitExchange.getAccountService();
      FuturesContract instrument = new FuturesContract("ETH/USDT/PERP");
      assertThat(accountService.setLeverage(instrument, 1)).isTrue();

      Throwable exception =
          catchThrowable(
              () ->
                  RateLimitDeadline.call(
                      Duration.ofMillis(100), () -> accountService.setLeverage(instrument, 1)));
      assertThat(exception).isInstanceOf(RateLimitTerminatedException.class);
      RateLimitTerminatedException terminated = (RateLimitTerminatedException) exception;
      assertThat(terminated.getReason())
          .isEqualTo(RateLimitTerminatedException.Reason.DEADLINE_EXCEEDED);
      assertThat(terminated.getDispatch()).isEqualTo(RateLimitTerminatedException.Dispatch.NOT_SENT);
      wireMockRule.verify(1, postRequestedFor(urlPathEqualTo("/v5/position/set-leverage")));
    }
  }

  private ExchangeSpecification specWithRemoteMetaDataDisabled() {
    ExchangeSpecification spec = new ExchangeSpecification(BybitExchange.class);
    spec.setShouldLoadRemoteMetaData(false);
    return spec;
  }

  private void assertSslUri(ExchangeSpecification spec, String expectedSslUri) {
    BybitExchange exchange = new BybitExchange();
    exchange.applySpecification(spec);
    assertThat(exchange.getExchangeSpecification().getSslUri()).isEqualTo(expectedSslUri);
  }
}
