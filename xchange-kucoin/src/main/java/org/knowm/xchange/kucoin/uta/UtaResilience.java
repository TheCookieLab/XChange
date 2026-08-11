package org.knowm.xchange.kucoin.uta;

import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.client.ResilienceUtils;

/**
 * UTA resilience registries.
 *
 * <p>KuCoin documents the Unified Account API pool as 200 requests/second at VIP 0, with per-call
 * weight and a 429000 provider code on exceed. Public market endpoints are governed separately.
 * Both limiters drain permissions when the provider reports throttling so a burst does not hammer
 * a rate-limited account.
 */
public class UtaResilience {

  public static final String UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER = "utaPublicEndpointLimit";

  public static final String UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER = "utaPrivateEndpointLimit";

  private UtaResilience() {}

  public static ResilienceRegistries createRegistries() {
    final ResilienceRegistries registries = new ResilienceRegistries();

    registries
        .rateLimiters()
        .rateLimiter(
            UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .limitForPeriod(30)
                .drainPermissionsOnResult(
                    e -> ResilienceUtils.matchesHttpCode(e, TOO_MANY_REQUESTS))
                .build());

    registries
        .rateLimiters()
        .rateLimiter(
            UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(200)
                .drainPermissionsOnResult(
                    e -> ResilienceUtils.matchesHttpCode(e, TOO_MANY_REQUESTS))
                .build());

    return registries;
  }
}
