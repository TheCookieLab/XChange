package org.knowm.xchange.cryptocom;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.client.ResilienceUtils;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Immutable per-API-method rate policy for Crypto.com Exchange v1 REST calls, opt-in through the
 * exchange specification parameter {@value #SPEC_PARAM}.
 *
 * <p>The parameter value is a comma-separated list of {@code method:maxCallsPerMinute} entries,
 * e.g. {@code private/create-order:120,private/get-order-detail:600}. A registered method's
 * limiter key is the API method name itself (the same string used in the request envelope), so raw
 * services pick the limiter up by calling the standard {@code withRateLimiter(rateLimiter(method))}
 * chain. Methods not listed are not limited by this policy (the provider-agnostic default
 * registries still apply). Limiter permits drain on provider HTTP 429 responses so backpressure
 * compounds rather than fighting the provider.
 *
 * <p>Only limits that can be expressed as a fixed per-minute budget are supported; nothing about
 * the policy allows the request envelope to be replayed.
 */
public final class CryptoComRatePolicy {

  /** Exchange specification parameter selecting the per-method rate policy. */
  public static final String SPEC_PARAM = "cryptocom_rate_policy";

  private final Map<String, Integer> limitsPerMinute;

  private CryptoComRatePolicy(Map<String, Integer> limitsPerMinute) {
    this.limitsPerMinute = Collections.unmodifiableMap(new LinkedHashMap<>(limitsPerMinute));
  }

  /** Parses {@code cryptocom_rate_policy}; empty/null yields an empty policy, malformed entries
   * fail fast with a descriptive {@link ExchangeException} so misconfiguration is audible at
   * exchange construction instead of silently disabling limits. */
  public static CryptoComRatePolicy parse(String specParamValue) {
    Map<String, Integer> limits = new LinkedHashMap<>();
    if (specParamValue == null || specParamValue.trim().isEmpty()) {
      return new CryptoComRatePolicy(limits);
    }
    for (String entry : specParamValue.split(",")) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int colon = trimmed.lastIndexOf(':');
      if (colon <= 0 || colon == trimmed.length() - 1) {
        throw new ExchangeException(
            "Invalid cryptocom_rate_policy entry '" + trimmed + "' (expected method:maxCallsPerMinute)");
      }
      String method = trimmed.substring(0, colon).trim();
      int limit;
      try {
        limit = Integer.parseInt(trimmed.substring(colon + 1).trim());
      } catch (NumberFormatException e) {
        throw new ExchangeException(
            "Invalid cryptocom_rate_policy limit for '" + method + "': " + trimmed, e);
      }
      if (method.isEmpty() || limit <= 0) {
        throw new ExchangeException(
            "Invalid cryptocom_rate_policy entry '" + trimmed + "' (method required, limit > 0)");
      }
      limits.put(method, limit);
    }
    return new CryptoComRatePolicy(limits);
  }

  /** Registers one rate limiter per configured method on the given registries. Idempotent. */
  public void registerRateLimiters(ResilienceRegistries registries) {
    for (Map.Entry<String, Integer> entry : limitsPerMinute.entrySet()) {
      registries
          .rateLimiters()
          .rateLimiter(
              entry.getKey(),
              RateLimiterConfig.from(registries.rateLimiters().getDefaultConfig())
                  .limitRefreshPeriod(Duration.ofMinutes(1))
                  .limitForPeriod(entry.getValue())
                  .drainPermissionsOnResult(
                      e -> ResilienceUtils.matchesHttpCode(e, jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS))
                  .build());
    }
  }

  public Map<String, Integer> limitsPerMinute() {
    return limitsPerMinute;
  }
}