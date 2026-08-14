package org.knowm.xchange.okx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, typed policy of per-endpoint OKX v5 REST rate limits.
 *
 * <p>Each entry maps an endpoint path (for example {@code /account/balance}) to its rate limit,
 * expressed as {@code limitForPeriod} requests per {@code refreshPeriodSeconds}. The limits are
 * provider data taken from the OKX v5 documentation (with the defensive reductions used
 * historically to avoid HTTP 429s) and are fixed when the policy is built; the policy itself is
 * read-only, so accidental mutation fails fast instead of corrupting shared state.
 *
 * <p>Extension point: endpoints added by later phases register their limits through {@link
 * Builder#limit(String, int, int)} before {@link Builder#build()}:
 *
 * <pre>{@code
 * OkxRateLimitPolicy policy =
 *     OkxRateLimitPolicy.builder()
 *         .limit(OkxAuthenticated.newEndpointPath, 20, 2)
 *         .build();
 * }</pre>
 *
 * <p>The existing {@link Okx#publicPathRateLimits} and {@link
 * OkxAuthenticated#privatePathRateLimits} policies are built this way; adding an entry to either
 * builder chain automatically registers the corresponding resilience4j rate limiter through {@link
 * OkxResilience#createRegistries()}.
 */
public final class OkxRateLimitPolicy {

  /**
   * Immutable rate limit for one endpoint path.
   *
   * @param limitForPeriod maximum number of requests per refresh period
   * @param refreshPeriodSeconds length of the refresh period in seconds
   */
  public record OkxRateLimit(int limitForPeriod, int refreshPeriodSeconds) {

    /** Compact constructor validating that both components are positive. */
    public OkxRateLimit {
      if (limitForPeriod <= 0) {
        throw new IllegalArgumentException("limitForPeriod must be positive: " + limitForPeriod);
      }
      if (refreshPeriodSeconds <= 0) {
        throw new IllegalArgumentException(
            "refreshPeriodSeconds must be positive: " + refreshPeriodSeconds);
      }
    }
  }

  private final Map<String, OkxRateLimit> limits;

  private OkxRateLimitPolicy(Map<String, OkxRateLimit> limits) {
    this.limits = Collections.unmodifiableMap(new LinkedHashMap<>(limits));
  }

  /** Creates a new empty policy builder. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the rate limit registered for the given endpoint path, if any.
   *
   * @param path the endpoint path, for example {@code /account/balance}
   * @return the registered limit, or empty when the path has no entry
   */
  public Optional<OkxRateLimit> rateLimitFor(String path) {
    return Optional.ofNullable(limits.get(path));
  }

  /**
   * Returns a read-only view of the path-to-limit registrations. The returned map cannot be
   * modified; attempts to do so throw {@link UnsupportedOperationException}.
   */
  public Map<String, OkxRateLimit> asMap() {
    return limits;
  }

  /** Mutable builder for {@link OkxRateLimitPolicy}. */
  public static final class Builder {

    private final Map<String, OkxRateLimit> limits = new LinkedHashMap<>();

    private Builder() {}

    /**
     * Registers (or replaces) the rate limit for one endpoint path.
     *
     * @param path the endpoint path, for example {@code /account/balance}
     * @param limitForPeriod maximum requests per refresh period
     * @param refreshPeriodSeconds refresh period length in seconds
     * @return this builder
     */
    public Builder limit(String path, int limitForPeriod, int refreshPeriodSeconds) {
      limits.put(
          Objects.requireNonNull(path, "path"),
          new OkxRateLimit(limitForPeriod, refreshPeriodSeconds));
      return this;
    }

    /**
     * Builds an immutable policy snapshot. Later mutations of this builder do not affect the
     * returned policy.
     */
    public OkxRateLimitPolicy build() {
      return new OkxRateLimitPolicy(limits);
    }
  }
}
