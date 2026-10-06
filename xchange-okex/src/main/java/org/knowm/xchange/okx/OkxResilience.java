package org.knowm.xchange.okx;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;

/**
 * Builds the resilience4j registries consumed by the OKX WebSocket trading service.
 *
 * <p>REST rate limiting is <strong>not</strong> configured here: it is owned by the universal
 * xchange-core rate limiter driven by {@link OkxRateLimitPolicy}. The limiters registered below
 * serve only {@code OkxStreamingTradeService} in {@code xchange-stream-okex}, whose WebSocket order
 * operations do not pass through the REST proxy and are outside the core REST rate-limit boundary.
 * They are keyed by the REST endpoint path constants and use the OKX documented 60 requests per 2
 * seconds for order placement, amendment and cancellation.
 */
public class OkxResilience {

  private static final int WEBSOCKET_ORDER_LIMIT_PER_PERIOD = 60;
  private static final Duration WEBSOCKET_ORDER_LIMIT_PERIOD = Duration.ofSeconds(2);

  public static ResilienceRegistries createRegistries() {
    final ResilienceRegistries registries = new ResilienceRegistries();

    for (String path :
        List.of(
            OkxAuthenticated.placeOrderPath,
            OkxAuthenticated.amendOrderPath,
            OkxAuthenticated.cancelOrderPath)) {
      registries
          .rateLimiters()
          .rateLimiter(
              path,
              RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                  .limitRefreshPeriod(WEBSOCKET_ORDER_LIMIT_PERIOD)
                  .limitForPeriod(WEBSOCKET_ORDER_LIMIT_PER_PERIOD)
                  .build());
    }

    return registries;
  }
}
