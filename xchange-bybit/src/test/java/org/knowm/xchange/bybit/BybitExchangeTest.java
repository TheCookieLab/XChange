package org.knowm.xchange.bybit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.knowm.xchange.bybit.BybitResilience.POSITION_SET_LEVERAGE_LINEAR_RATE_LIMITER;

import com.github.tomakehurst.wiremock.matching.ContainsPattern;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.io.IOException;
import java.time.Duration;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.config.BybitEnvironment;
import org.knowm.xchange.bybit.service.BaseWiremockTest;
import org.knowm.xchange.bybit.service.BybitAccountService;
import org.knowm.xchange.derivative.FuturesContract;

public class BybitExchangeTest extends BaseWiremockTest {

  @Test
  public void testSymbolLoading() throws IOException {
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

  @Test
  public void rateLimiterTest() throws IOException {
    Exchange bybitExchange = createExchange();
    bybitExchange
        .getResilienceRegistries()
        .rateLimiters()
        .replace(
            POSITION_SET_LEVERAGE_LINEAR_RATE_LIMITER,
            RateLimiter.of(
                POSITION_SET_LEVERAGE_LINEAR_RATE_LIMITER,
                RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .limitForPeriod(1)
                    .timeoutDuration(Duration.ofMillis(1))
                    .build()));
    initPostStub("/v5/position/set-leverage", "/setLeverage.json5");
    BybitAccountService bybitAccountService =
        (BybitAccountService) bybitExchange.getAccountService();
    boolean bybitSetLeverageBybitResult;
    Throwable exception = null;
    for (int i = 0; i <= 2; i++) {
      exception =
          catchThrowable(
              () -> bybitAccountService.setLeverage(new FuturesContract("ETH/USDT/PERP"), 1));
    }
    assertThat(exception).isInstanceOf(RequestNotPermitted.class);
  }
}
