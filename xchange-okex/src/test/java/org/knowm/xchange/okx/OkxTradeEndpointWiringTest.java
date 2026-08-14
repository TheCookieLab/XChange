package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Offline wiring test: every Phase 4 trade endpoint must be registered in the private rate-limit
 * policy with the rate documented by OKX, so {@link OkxResilience#createRegistries()} installs a
 * limiter for it.
 */
public class OkxTradeEndpointWiringTest {

  private static OkxRateLimitPolicy.OkxRateLimit limit(String path) {
    OkxRateLimitPolicy.OkxRateLimit rate = OkxAuthenticated.privatePathRateLimits.asMap().get(path);
    assertThat(rate).as("private rate limit for %s", path).isNotNull();
    return rate;
  }

  @Test
  public void testFillsEndpointHasDocumentedLimit() {
    assertThat(limit(OkxAuthenticated.fillsPath).limitForPeriod()).isEqualTo(60);
    assertThat(limit(OkxAuthenticated.fillsPath).refreshPeriodSeconds()).isEqualTo(2);
  }

  @Test
  public void testFillsHistoryEndpointHasDocumentedLimit() {
    assertThat(limit(OkxAuthenticated.fillsHistoryPath).limitForPeriod()).isEqualTo(10);
    assertThat(limit(OkxAuthenticated.fillsHistoryPath).refreshPeriodSeconds()).isEqualTo(2);
  }

  @Test
  public void testOrderAlgoEndpointsHaveDocumentedLimits() {
    assertThat(limit(OkxAuthenticated.orderAlgoPath).limitForPeriod()).isEqualTo(60);
    assertThat(limit(OkxAuthenticated.orderAlgoPath).refreshPeriodSeconds()).isEqualTo(2);
    assertThat(limit(OkxAuthenticated.cancelAlgosPath).limitForPeriod()).isEqualTo(60);
    assertThat(limit(OkxAuthenticated.cancelAlgosPath).refreshPeriodSeconds()).isEqualTo(2);
    assertThat(limit(OkxAuthenticated.amendAlgosPath).limitForPeriod()).isEqualTo(60);
    assertThat(limit(OkxAuthenticated.amendAlgosPath).refreshPeriodSeconds()).isEqualTo(2);
  }

  @Test
  public void testAlgoQueryEndpointsHaveDocumentedLimits() {
    assertThat(limit(OkxAuthenticated.ordersAlgoPendingPath).limitForPeriod()).isEqualTo(10);
    assertThat(limit(OkxAuthenticated.ordersAlgoPendingPath).refreshPeriodSeconds()).isEqualTo(2);
    assertThat(limit(OkxAuthenticated.ordersAlgoHistoryPath).limitForPeriod()).isEqualTo(20);
    assertThat(limit(OkxAuthenticated.ordersAlgoHistoryPath).refreshPeriodSeconds()).isEqualTo(2);
  }
}
