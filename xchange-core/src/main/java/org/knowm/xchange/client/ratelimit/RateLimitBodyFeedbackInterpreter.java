package org.knowm.xchange.client.ratelimit;

/**
 * Policy-owned translation of a decoded attempt outcome into {@link RateLimitFeedback}, for
 * exchanges that report a rate rejection in the response body (for example with HTTP 200) rather
 * than through the HTTP status interpreted by {@link RateLimitFeedbackInterpreter}.
 *
 * <p>It is consulted exactly once per wire attempt, after the attempt returned or threw, and its
 * feedback is combined with the HTTP feedback of the same attempt (the stronger wins). A rejection
 * reported here starts the same cooldown as an HTTP rejection; the operation is replayed only when
 * it is declared replay-safe ({@link RateLimitOperation#isReplayOnRateLimit()}), with a fresh
 * admission and a freshly built request.
 *
 * <p>Implementations must be stateless and side-effect free, and must not depend on transport
 * types: they see the attempt's decoded result (for example a module response DTO) or the
 * exception it threw (for example a module exception decoded from an error body).
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface RateLimitBodyFeedbackInterpreter {

  /**
   * Interprets one attempt outcome. Exactly one of {@code result} and {@code failure} describes
   * the outcome: {@code failure} is non-null when the attempt threw, otherwise {@code result} is
   * the returned value (which may itself be {@code null}).
   *
   * @param result the decoded value returned by the attempt, or {@code null}
   * @param failure the exception thrown by the attempt, or {@code null} when it returned
   * @return the feedback, never {@code null}
   */
  RateLimitFeedback interpret(Object result, Exception failure);

  /**
   * @return an interpreter that never reports rate pressure
   */
  static RateLimitBodyFeedbackInterpreter none() {
    return (result, failure) -> RateLimitFeedback.NONE;
  }
}
