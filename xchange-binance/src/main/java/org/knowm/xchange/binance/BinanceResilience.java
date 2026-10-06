package org.knowm.xchange.binance;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import org.knowm.xchange.client.ResilienceRegistries;

/**
 * Resilience registries for Binance.
 *
 * <p>REST rate limiting is owned by the universal xchange-core rate limiter configured through
 * {@link BinanceRateLimitPolicy}; no REST call path consults the resilience4j rate limiters
 * registered here. These limiters (and the matching constants) are kept only because the
 * WebSocket API order-placement path in {@code xchange-stream-binance} still consumes them.
 */
public final class BinanceResilience {

  public static final String REQUEST_WEIGHT_RATE_LIMITER = "requestWeight";
  public static final String ORDERS_PER_10_SECONDS_RATE_LIMITER = "ordersPer10Seconds";
  public static final String ORDERS_PER_DAY_RATE_LIMITER = "ordersPerDay";
  public static final String ORDERS_PER_MINUTE_RATE_LIMITER = "ordersPerMINUTE";

  private BinanceResilience() {}

  public static ResilienceRegistries createRegistries() {
    ResilienceRegistries registries = new ResilienceRegistries();
    registries
        .rateLimiters()
        .rateLimiter(
            REQUEST_WEIGHT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .timeoutDuration(Duration.ofMinutes(1))
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(6000)
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDERS_PER_10_SECONDS_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .limitForPeriod(100)
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDERS_PER_DAY_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(200000)
                .build());
    return registries;
  }

  public static ResilienceRegistries createRegistriesFuture() {
    ResilienceRegistries registries = new ResilienceRegistries();
    registries
        .rateLimiters()
        .rateLimiter(
            REQUEST_WEIGHT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .timeoutDuration(Duration.ofMinutes(1))
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(2400)
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDERS_PER_10_SECONDS_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .limitForPeriod(300)
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDERS_PER_MINUTE_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(1200)
                .build());
    return registries;
  }
}
