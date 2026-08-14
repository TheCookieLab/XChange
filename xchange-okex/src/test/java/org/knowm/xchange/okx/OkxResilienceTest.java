package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.ratelimiter.RateLimiter;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.knowm.xchange.client.ResilienceRegistries;

/** Offline tests for {@link OkxResilience} consuming the typed endpoint policies. */
public class OkxResilienceTest {

  private static Map<String, RateLimiter> limitersByPath(ResilienceRegistries registries) {
    return registries.rateLimiters().getAllRateLimiters().stream()
        .collect(Collectors.toMap(RateLimiter::getName, limiter -> limiter));
  }

  @Test
  public void testCreateRegistriesRegistersEveryPublicPolicyEntry() {
    Map<String, RateLimiter> limiters =
        limitersByPath(OkxResilience.createRegistries());

    Okx.publicPathRateLimits.asMap().forEach(
        (path, limit) -> {
          RateLimiter limiter = limiters.get(path);
          assertThat(limiter).as("public limiter for %s", path).isNotNull();
          assertThat(limiter.getRateLimiterConfig().getLimitForPeriod())
              .as("public limit for %s", path)
              .isEqualTo(limit.limitForPeriod());
          assertThat(limiter.getRateLimiterConfig().getLimitRefreshPeriod())
              .as("public refresh for %s", path)
              .isEqualTo(Duration.ofSeconds(limit.refreshPeriodSeconds()));
        });
  }

  @Test
  public void testCreateRegistriesRegistersEveryPrivatePolicyEntry() {
    Map<String, RateLimiter> limiters =
        limitersByPath(OkxResilience.createRegistries());

    OkxAuthenticated.privatePathRateLimits.asMap().forEach(
        (path, limit) -> {
          RateLimiter limiter = limiters.get(path);
          assertThat(limiter).as("private limiter for %s", path).isNotNull();
          assertThat(limiter.getRateLimiterConfig().getLimitForPeriod())
              .as("private limit for %s", path)
              .isEqualTo(limit.limitForPeriod());
          assertThat(limiter.getRateLimiterConfig().getLimitRefreshPeriod())
              .as("private refresh for %s", path)
              .isEqualTo(Duration.ofSeconds(limit.refreshPeriodSeconds()));
        });
  }

  @Test
  public void testRegisterRateLimiterAddsEndpointAtRuntime() {
    ResilienceRegistries registries = new ResilienceRegistries();

    OkxResilience.registerRateLimiter(
        registries, "/phase/4/new-endpoint", new OkxRateLimitPolicy.OkxRateLimit(20, 2));

    RateLimiter limiter = registries.rateLimiters().rateLimiter("/phase/4/new-endpoint");
    assertThat(limiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(20);
    assertThat(limiter.getRateLimiterConfig().getLimitRefreshPeriod())
        .isEqualTo(Duration.ofSeconds(2));
  }
}
