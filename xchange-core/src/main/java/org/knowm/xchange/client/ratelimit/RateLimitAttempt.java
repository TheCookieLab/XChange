package org.knowm.xchange.client.ratelimit;

/**
 * One wire attempt of a rate-limited operation. The context calls {@link #run} only after every
 * required budget admitted the request and the final deadline/cancellation check passed, so any
 * dynamic timestamp, nonce, JWT or signature MUST be created inside {@code run}. A replay calls
 * {@code run} again and therefore signs anew.
 *
 * @param <T> result type
 * @since 1.0.3
 */
@FunctionalInterface
public interface RateLimitAttempt<T> {

  /**
   * Performs the wire attempt.
   *
   * @param observer receives the response status/headers or normalized feedback of this attempt
   * @return the result
   * @throws Exception any failure; non-rate failures propagate to the caller unchanged
   */
  T run(RateLimitAttemptObserver observer) throws Exception;
}
