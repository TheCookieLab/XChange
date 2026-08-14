package org.knowm.xchange.okx;

import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.Map;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.client.ResilienceUtils;

/**
 * Builds the resilience4j registries backing OKX v5 REST calls.
 *
 * <p>Rate limiters are derived from the typed endpoint policies {@link Okx#publicPathRateLimits}
 * and {@link OkxAuthenticated#privatePathRateLimits}; the registry key for each limiter is the
 * endpoint path, so raw services reference limiters via {@code rateLimiter(<Path constant>)}.
 *
 * <p>Extension point for later phases: endpoints registered in either policy (via {@link
 * OkxRateLimitPolicy.Builder#limit(String, int, int)}) are picked up automatically by {@link
 * #createRegistries()}. Endpoints whose limits cannot be expressed in the static policies can be
 * registered at runtime with {@link #registerRateLimiter(ResilienceRegistries, String,
 * OkxRateLimitPolicy.OkxRateLimit)}.
 */
public class OkxResilience {

  /**
   * Registers (or replaces) the rate limiter for one endpoint path on the given registries, using
   * the same configuration conventions as the static policy entries (default config, refresh
   * period, limit, and permission drain on HTTP 429).
   *
   * @param registries the registries to register on
   * @param path the endpoint path used as registry key, for example {@code /trade/order}
   * @param rateLimit the limit to apply
   */
  public static void registerRateLimiter(
      ResilienceRegistries registries, String path, OkxRateLimitPolicy.OkxRateLimit rateLimit) {
    registries
        .rateLimiters()
        .rateLimiter(
            path,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(rateLimit.refreshPeriodSeconds()))
                .limitForPeriod(rateLimit.limitForPeriod())
                .drainPermissionsOnResult(
                    e -> ResilienceUtils.matchesHttpCode(e, TOO_MANY_REQUESTS))
                .build());
  }

  public static ResilienceRegistries createRegistries() {
    final ResilienceRegistries registries = new ResilienceRegistries();

    for (Map.Entry<String, OkxRateLimitPolicy.OkxRateLimit> entry :
        Okx.publicPathRateLimits.asMap().entrySet()) {
      registerRateLimiter(registries, entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String, OkxRateLimitPolicy.OkxRateLimit> entry :
        OkxAuthenticated.privatePathRateLimits.asMap().entrySet()) {
      registerRateLimiter(registries, entry.getKey(), entry.getValue());
    }

    return registries;
  }
}
