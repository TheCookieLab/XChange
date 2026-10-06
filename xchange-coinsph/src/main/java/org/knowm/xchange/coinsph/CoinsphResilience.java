package org.knowm.xchange.coinsph;

import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.coinsph.dto.CoinsphException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import si.mazi.rescu.HttpStatusExceptionSupport;
import si.mazi.rescu.HttpStatusIOException;

/**
 * Retry configuration of the Coins.ph module.
 *
 * <p>Rate limiting is not configured here: the universal rate limiter of xchange-core, driven by
 * {@link CoinsphRateLimitPolicy}, owns admission, 429/418 cooldown and replay. A retry must never
 * resend a rate-rejected request, because that would be an unadmitted wire attempt.
 */
public final class CoinsphResilience {

  /** Name of the retry configuration for transient, non-rate failures. */
  public static final String GENERAL_RETRY = "generalRetry";

  private CoinsphResilience() {}

  public static ResilienceRegistries createRegistries() {
    final ResilienceRegistries registries = new ResilienceRegistries();

    registries
        .retries()
        .retry(
            GENERAL_RETRY,
            RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(HttpStatusIOException.class, CoinsphException.class)
                .ignoreExceptions(RateLimitExceededException.class)
                .retryOnException(throwable -> !isRateRejection(throwable))
                .build());

    return registries;
  }

  /**
   * @return whether the failure is an HTTP 429/418 rate rejection, which the core rate limiter
   *     owns
   */
  static boolean isRateRejection(Throwable throwable) {
    if (throwable instanceof RateLimitExceededException) {
      return true;
    }
    int status;
    if (throwable instanceof HttpStatusIOException) {
      status = ((HttpStatusIOException) throwable).getHttpStatusCode();
    } else if (throwable instanceof HttpStatusExceptionSupport) {
      status = ((HttpStatusExceptionSupport) throwable).getHttpStatusCode();
    } else {
      return false;
    }
    return status == 429 || status == 418;
  }
}
