package org.knowm.xchange.binance.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.binance.config.BinanceProductFamily;
import org.knowm.xchange.binance.error.BinanceRetryClassification;

public class BinanceRateLimitTelemetryTest {

  @Test
  public void testParsesStandardHeadersCaseInsensitively() {
    Map<String, List<String>> headers =
        Map.of(
            "x-mbx-used-weight-1m", List.of("120"),
            "X-MBX-ORDER-COUNT-10S", List.of("7"),
            "retry-after", List.of("30"));

    BinanceRateLimitTelemetry telemetry = BinanceRateLimitTelemetry.fromHeaders(headers);

    assertThat(telemetry.usedWeight1m()).isEqualTo(120);
    assertThat(telemetry.orderCount10s()).isEqualTo(7);
    assertThat(telemetry.retryAfterMillis()).isEqualTo(30);
    assertThat(telemetry.banned()).isFalse();
  }

  @Test
  public void testMissingHeadersAreNull() {
    BinanceRateLimitTelemetry telemetry = BinanceRateLimitTelemetry.fromHeaders(Map.of());

    assertThat(telemetry.usedWeight1m()).isNull();
    assertThat(telemetry.orderCount10s()).isNull();
    assertThat(telemetry.retryAfterMillis()).isNull();
  }

  @Test
  public void testNullHeadersAreTolerated() {
    assertThat(BinanceRateLimitTelemetry.fromHeaders(null).usedWeight1m()).isNull();
  }

  @Test
  public void testNonNumericHeaderTreatedAsAbsent() {
    Map<String, List<String>> headers = Map.of("x-mbx-used-weight-1m", List.of("n/a"));

    assertThat(BinanceRateLimitTelemetry.fromHeaders(headers).usedWeight1m()).isNull();
  }

  @Test
  public void testEndpointPolicyRegistryLookupAndDefault() {
    BinanceEndpointPolicy placement =
        BinanceEndpointPolicies.policy(BinanceProductFamily.SPOT, "orderPlacement");
    assertThat(placement.retry()).isEqualTo(BinanceRetryClassification.RECONCILE);
    assertThat(placement.orderCount10s()).isEqualTo(10);

    BinanceEndpointPolicy marketData =
        BinanceEndpointPolicies.policy(BinanceProductFamily.SPOT, "marketData");
    assertThat(marketData.retry()).isEqualTo(BinanceRetryClassification.REPLAY_SAFE);

    BinanceEndpointPolicy unknown =
        BinanceEndpointPolicies.policy(BinanceProductFamily.SPOT, "noSuchOperation");
    assertThat(unknown.retry()).isEqualTo(BinanceRetryClassification.REPLAY_SAFE);
    assertThat(unknown.weight()).isZero();
  }

  @Test
  public void testFuturesPlacementIsNeverReplaySafe() {
    assertThat(
            BinanceEndpointPolicies.policy(BinanceProductFamily.USDM, "orderPlacement").retry())
        .isEqualTo(BinanceRetryClassification.RECONCILE);
    assertThat(
            BinanceEndpointPolicies.policy(BinanceProductFamily.COINM, "orderPlacement").retry())
        .isEqualTo(BinanceRetryClassification.RECONCILE);
    assertThat(
            BinanceEndpointPolicies.policy(BinanceProductFamily.PORTFOLIO_MARGIN, "orderPlacement")
                .retry())
        .isEqualTo(BinanceRetryClassification.RECONCILE);
  }
}
