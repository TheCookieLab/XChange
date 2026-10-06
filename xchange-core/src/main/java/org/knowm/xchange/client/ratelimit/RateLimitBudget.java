package org.knowm.xchange.client.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable definition of one rate budget: a namespaced identity, the scope it is keyed by and the
 * actual rate law (token bucket, aligned fixed window or rolling window).
 *
 * <p>Two budgets are equal when every part of the definition is equal, so a shared {@link
 * RateLimitContext} can detect conflicting definitions for the same {@link #getId() id}.
 *
 * <p>Costs are whole units. A request whose cost exceeds {@link #getCapacity()} can never be
 * admitted and is rejected immediately.
 *
 * @since 1.0.3
 */
public final class RateLimitBudget {

  /**
   * Which identity a budget is keyed by.
   *
   * @since 1.0.3
   */
  public enum ScopeKind {
    /** One state per process egress (local contribution to a remote IP quota). */
    EGRESS,
    /** One state per opaque user scope supplied to {@link RateLimitContext#execute}. */
    USER,
    /** One state per context, independent of user. */
    GLOBAL
  }

  /**
   * The rate law of a budget.
   *
   * @since 1.0.3
   */
  public enum Law {
    /** Continuous refill up to a burst capacity; starts full. */
    TOKEN_BUCKET,
    /** At most {@code limit} units per window; windows are aligned to wall-clock multiples. */
    FIXED_WINDOW,
    /** At most {@code limit} units in any interval of window length. */
    ROLLING_WINDOW
  }

  private final String id;
  private final ScopeKind scopeKind;
  private final Law law;
  private final long capacity;
  private final long refillAmount;
  private final Duration period;
  private final long periodNanos;

  private RateLimitBudget(
      String id, ScopeKind scopeKind, Law law, long capacity, long refillAmount, Duration period) {
    if (Objects.requireNonNull(id, "id").trim().isEmpty()) {
      throw new IllegalArgumentException("budget id must not be blank");
    }
    this.id = id;
    this.scopeKind = Objects.requireNonNull(scopeKind, "scopeKind");
    this.law = law;
    if (capacity < 1) {
      throw new IllegalArgumentException("budget " + id + ": capacity/limit must be >= 1");
    }
    if (refillAmount < 1) {
      throw new IllegalArgumentException("budget " + id + ": refill amount must be >= 1");
    }
    this.capacity = capacity;
    this.refillAmount = refillAmount;
    this.period = Objects.requireNonNull(period, "period");
    if (period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException("budget " + id + ": period must be positive");
    }
    try {
      this.periodNanos = period.toNanos();
      if (periodNanos > RateLimitScheduler.MAX_DELAY_NANOS) {
        throw new IllegalArgumentException("budget " + id + ": period is too large");
      }
      Math.multiplyExact(capacity, periodNanos);
      Math.multiplyExact(refillAmount, periodNanos);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "budget " + id + ": capacity and period are too large for exact arithmetic", e);
    }
  }

  /**
   * Token bucket that starts full and refills {@code refillAmount} units every {@code
   * refillPeriod}, continuously, up to {@code capacity}.
   *
   * @param id namespaced identity, for example {@code "coinbase.brokerage.user"}
   * @param scopeKind which identity the budget is keyed by
   * @param capacity burst capacity in units
   * @param refillAmount units added per refill period
   * @param refillPeriod refill period
   * @return the budget
   * @throws IllegalArgumentException if any value is out of range or overflows exact arithmetic
   */
  public static RateLimitBudget tokenBucket(
      String id, ScopeKind scopeKind, long capacity, long refillAmount, Duration refillPeriod) {
    return new RateLimitBudget(id, scopeKind, Law.TOKEN_BUCKET, capacity, refillAmount, refillPeriod);
  }

  /**
   * Fixed window: at most {@code limit} units per window. Windows are aligned to multiples of
   * {@code window} on the wall clock, as most providers align them.
   *
   * @param id namespaced identity
   * @param scopeKind which identity the budget is keyed by
   * @param limit units per window
   * @param window window length
   * @return the budget
   * @throws IllegalArgumentException if any value is out of range or overflows exact arithmetic
   */
  public static RateLimitBudget fixedWindow(
      String id, ScopeKind scopeKind, long limit, Duration window) {
    return new RateLimitBudget(id, scopeKind, Law.FIXED_WINDOW, limit, limit, window);
  }

  /**
   * Rolling window: at most {@code limit} units in any interval of length {@code window}.
   *
   * @param id namespaced identity
   * @param scopeKind which identity the budget is keyed by
   * @param limit units per window
   * @param window window length
   * @return the budget
   * @throws IllegalArgumentException if any value is out of range or overflows exact arithmetic
   */
  public static RateLimitBudget rollingWindow(
      String id, ScopeKind scopeKind, long limit, Duration window) {
    return new RateLimitBudget(id, scopeKind, Law.ROLLING_WINDOW, limit, limit, window);
  }

  /**
   * @return the namespaced budget identity
   */
  public String getId() {
    return id;
  }

  /**
   * @return which identity the budget is keyed by
   */
  public ScopeKind getScopeKind() {
    return scopeKind;
  }

  /**
   * @return the rate law
   */
  public Law getLaw() {
    return law;
  }

  /**
   * @return burst capacity (token bucket) or limit (windows); also the largest admissible cost
   */
  public long getCapacity() {
    return capacity;
  }

  /**
   * @return units added per {@link #getPeriod() period}; equals the limit for windows
   */
  public long getRefillAmount() {
    return refillAmount;
  }

  /**
   * @return refill period (token bucket) or window length (windows)
   */
  public Duration getPeriod() {
    return period;
  }

  long periodNanos() {
    return periodNanos;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RateLimitBudget)) {
      return false;
    }
    RateLimitBudget other = (RateLimitBudget) o;
    return capacity == other.capacity
        && refillAmount == other.refillAmount
        && periodNanos == other.periodNanos
        && law == other.law
        && scopeKind == other.scopeKind
        && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, scopeKind, law, capacity, refillAmount, periodNanos);
  }

  @Override
  public String toString() {
    return "RateLimitBudget{id="
        + id
        + ", scopeKind="
        + scopeKind
        + ", law="
        + law
        + ", capacity="
        + capacity
        + ", refillAmount="
        + refillAmount
        + ", period="
        + period
        + '}';
  }
}
