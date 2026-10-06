package org.knowm.xchange.client.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Function;

/**
 * Policy-owned translation of an HTTP response into {@link RateLimitFeedback}. A status such as
 * 429 or 418 is only given a meaning by a policy that declares it; there is no universal default.
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface RateLimitFeedbackInterpreter {

  /**
   * Interprets one HTTP response.
   *
   * @param status HTTP status code
   * @param headerLookup case-insensitive header lookup returning {@code null} when absent
   * @param wallNow current wall-clock instant, for HTTP-date evaluation
   * @return the feedback, never {@code null}
   */
  RateLimitFeedback interpret(int status, Function<String, String> headerLookup, Instant wallNow);

  /**
   * @return an interpreter that never reports rate pressure
   */
  static RateLimitFeedbackInterpreter none() {
    return (status, headerLookup, wallNow) -> RateLimitFeedback.NONE;
  }

  /**
   * Interpreter that declares the given statuses as rate rejection or ban and honors {@code
   * Retry-After} (delta-seconds or HTTP-date). All other statuses are not rate related.
   *
   * @param rejectedStatuses statuses meaning "rate rejected, replay may be allowed" (for example
   *     429)
   * @param bannedStatuses statuses meaning "banned, never replay" (for example 418 where the
   *     provider documents it)
   * @return the interpreter
   */
  static RateLimitFeedbackInterpreter statuses(
      Set<Integer> rejectedStatuses, Set<Integer> bannedStatuses) {
    Set<Integer> rejected = Set.copyOf(rejectedStatuses);
    Set<Integer> banned = Set.copyOf(bannedStatuses);
    return (status, headerLookup, wallNow) -> {
      boolean isBan = banned.contains(status);
      if (!isBan && !rejected.contains(status)) {
        return RateLimitFeedback.NONE;
      }
      String raw = headerLookup.apply("Retry-After");
      if (raw == null) {
        raw = headerLookup.apply("retry-after");
      }
      Duration delay = RateLimitFeedback.parseRetryAfter(raw, wallNow);
      return isBan ? RateLimitFeedback.banned(delay) : RateLimitFeedback.rejected(delay);
    };
  }
}
