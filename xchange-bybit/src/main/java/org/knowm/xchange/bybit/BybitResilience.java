package org.knowm.xchange.bybit;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import org.knowm.xchange.client.ResilienceRegistries;

/**
 * Per-category resilience4j limiters used only by the WebSocket trading transport of {@code
 * xchange-stream-bybit}, which does not pass through the REST rate-limit boundary. REST requests
 * are paced exclusively by {@link BybitRateLimitPolicy} in xchange-core.
 *
 * @see <a href="https://bybit-exchange.github.io/docs/v5/rate-limit">Bybit rate limits</a>
 */
public class BybitResilience {

  // /v5/order/create
  public static final String ORDER_CREATE_LINEAR_AND_INVERSE_RATE_LIMITER =
      "orderCreateLinearAndInverse";
  public static final String ORDER_CREATE_SPOT_RATE_LIMITER = "orderCreateSpot";
  public static final String ORDER_CREATE_OPTION_LIMITER = "orderCreateOption";

  // /v5/order/amend
  public static final String ORDER_AMEND_LINEAR_AND_INVERSE_RATE_LIMITER =
      "orderAmendLinearAndInverse";
  public static final String ORDER_AMEND_SPOT_RATE_LIMITER = "orderAmendSpot";
  public static final String ORDER_AMEND_OPTION_LIMITER = "orderAmendOption";

  // /v5/order/amend-batch
  public static final String BATCH_ORDER_AMEND_LINEAR_AND_INVERSE_RATE_LIMITER =
      "batchOrderAmendLinearAndInverse";
  public static final String BATCH_ORDER_AMEND_SPOT_RATE_LIMITER = "batchOrderAmendSpot";
  public static final String BATCH_ORDER_AMEND_OPTION_LIMITER = "batchOrderAmendOption";

  // /v5/order/cancel
  public static final String ORDER_CANCEL_LINEAR_AND_INVERSE_RATE_LIMITER =
      "orderCancelLinearAndInverse";
  public static final String ORDER_CANCEL_SPOT_RATE_LIMITER = "orderCancelSpot";
  public static final String ORDER_CANCEL_OPTION_LIMITER = "orderCancelOption";

  public static ResilienceRegistries createRegistries() {
    ResilienceRegistries registries = new ResilienceRegistries();

    // /order/create
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_CREATE_LINEAR_AND_INVERSE_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_CREATE_SPOT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(20)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_CREATE_OPTION_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());

    // /order/amend
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_AMEND_LINEAR_AND_INVERSE_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_AMEND_SPOT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_AMEND_OPTION_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());

    // /order/amend-batch
    registries
        .rateLimiters()
        .rateLimiter(
            BATCH_ORDER_AMEND_LINEAR_AND_INVERSE_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            BATCH_ORDER_AMEND_SPOT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(20)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            BATCH_ORDER_AMEND_OPTION_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());

    // /order/cancel
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_CANCEL_LINEAR_AND_INVERSE_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_CANCEL_SPOT_RATE_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(20)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    registries
        .rateLimiters()
        .rateLimiter(
            ORDER_CANCEL_OPTION_LIMITER,
            RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
    return registries;
  }
}
