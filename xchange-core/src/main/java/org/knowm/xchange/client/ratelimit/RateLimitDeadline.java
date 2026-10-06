package org.knowm.xchange.client.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Thread-scoped operation deadline that callers with an existing domain deadline can impose on
 * every rate-limited operation executed inside the scope. A scope can only <em>tighten</em> the
 * deadline: nested scopes and the policy's own maximum wait never extend it.
 *
 * <p>The deadline is kept in the {@link System#nanoTime()} domain; the {@link RateLimitContext}
 * converts the remaining time into its own monotonic clock when an operation starts.
 *
 * @since 1.0.3
 */
public final class RateLimitDeadline {

  private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

  private RateLimitDeadline() {}

  /**
   * Runs {@code action} with a deadline of {@code limit} from now.
   *
   * @param limit remaining time; zero or negative means already expired
   * @param action the work, typically a call that performs rate-limited operations
   * @param <T> result type
   * @return the action's result
   * @throws Exception whatever the action throws
   */
  public static <T> T call(Duration limit, Callable<T> action) throws Exception {
    Objects.requireNonNull(limit, "limit");
    long nanos;
    try {
      nanos = Math.min(limit.toNanos(), RateLimitScheduler.MAX_DELAY_NANOS);
    } catch (ArithmeticException e) {
      nanos = limit.isNegative() ? -RateLimitScheduler.MAX_DELAY_NANOS : RateLimitScheduler.MAX_DELAY_NANOS;
    }
    return callUntil(System.nanoTime() + nanos, action);
  }

  /**
   * Runs {@code action} with an absolute deadline.
   *
   * @param nanoDeadline deadline in the {@link System#nanoTime()} domain
   * @param action the work
   * @param <T> result type
   * @return the action's result
   * @throws Exception whatever the action throws
   */
  public static <T> T callUntil(long nanoDeadline, Callable<T> action) throws Exception {
    Objects.requireNonNull(action, "action");
    Long previous = DEADLINE.get();
    long effective = previous != null && previous - nanoDeadline < 0 ? previous : nanoDeadline;
    DEADLINE.set(effective);
    try {
      return action.call();
    } finally {
      if (previous == null) {
        DEADLINE.remove();
      } else {
        DEADLINE.set(previous);
      }
    }
  }

  /** Remaining nanoseconds of the current scope, or {@code null} if no scope is active. */
  static Long remainingNanos() {
    Long deadline = DEADLINE.get();
    return deadline == null ? null : deadline - System.nanoTime();
  }
}
