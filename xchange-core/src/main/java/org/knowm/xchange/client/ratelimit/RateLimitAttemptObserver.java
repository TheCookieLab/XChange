package org.knowm.xchange.client.ratelimit;

import java.util.function.Function;

/**
 * Receives what one wire attempt observed. Observing rate rejection or a ban makes the attempt a
 * rejected attempt whether {@link RateLimitAttempt#run} then throws or returns. Implementations are
 * thread-safe.
 *
 * @since 1.0.3
 */
public interface RateLimitAttemptObserver {

  /**
   * Reports the exact HTTP response of this attempt before any exception translation. The
   * policy's {@link RateLimitFeedbackInterpreter} gives the status its meaning.
   *
   * @param status HTTP status code
   * @param headerLookup case-insensitive header lookup returning {@code null} when absent
   */
  void observeHttpResponse(int status, Function<String, String> headerLookup);

  /**
   * Reports module-normalized feedback (for example JSON-RPC error codes).
   *
   * @param feedback the feedback
   */
  void observeFeedback(RateLimitFeedback feedback);

  /** Marks the attempt as dispatched with an unknown outcome; it is never replayed. */
  void markUncertain();
}
