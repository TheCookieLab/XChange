package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.ratelimiter.RateLimiter;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.knowm.xchange.client.ResilienceRegistries;

/**
 * Offline tests for {@link OkxResilience}: the registries serve only the WebSocket order channels,
 * since REST rate limiting is owned by the core limiter and {@link OkxRateLimitPolicy}.
 */
public class OkxResilienceTest {

  @Test
  public void registriesHoldOnlyTheWebSocketOrderLimiters() {
    ResilienceRegistries registries = OkxResilience.createRegistries();

    Set<String> names =
        registries.rateLimiters().getAllRateLimiters().stream()
            .map(RateLimiter::getName)
            .collect(Collectors.toSet());

    assertThat(names)
        .containsExactlyInAnyOrder(
            OkxAuthenticated.placeOrderPath,
            OkxAuthenticated.amendOrderPath,
            OkxAuthenticated.cancelOrderPath);
  }

  @Test
  public void webSocketOrderLimitersUseTheDocumentedSixtyPerTwoSeconds() {
    ResilienceRegistries registries = OkxResilience.createRegistries();

    for (String path :
        new String[] {
          OkxAuthenticated.placeOrderPath,
          OkxAuthenticated.amendOrderPath,
          OkxAuthenticated.cancelOrderPath
        }) {
      RateLimiter limiter = registries.rateLimiters().rateLimiter(path);
      assertThat(limiter.getRateLimiterConfig().getLimitForPeriod()).as(path).isEqualTo(60);
      assertThat(limiter.getRateLimiterConfig().getLimitRefreshPeriod())
          .as(path)
          .isEqualTo(Duration.ofSeconds(2));
    }
  }
}
