package org.knowm.xchange.client.ratelimit;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

/** Collects the feedback of exactly one wire attempt. Owned by the attempt's thread. */
final class AttemptRecorder implements RateLimitAttemptObserver {

  private final RateLimitFeedbackInterpreter interpreter;
  private final Clock wall;
  private RateLimitFeedback feedback = RateLimitFeedback.NONE;
  private boolean uncertain;

  AttemptRecorder(RateLimitFeedbackInterpreter interpreter, Clock wall) {
    this.interpreter = interpreter;
    this.wall = wall;
  }

  @Override
  public void observeHttpResponse(int status, Function<String, String> headerLookup) {
    Objects.requireNonNull(headerLookup, "headerLookup");
    RateLimitFeedback interpreted = interpreter.interpret(status, headerLookup, wall.instant());
    observeFeedback(Objects.requireNonNull(interpreted, "interpreted feedback"));
  }

  @Override
  public void observeFeedback(RateLimitFeedback observed) {
    feedback = RateLimitFeedback.strongest(feedback, Objects.requireNonNull(observed, "feedback"));
  }

  @Override
  public void markUncertain() {
    uncertain = true;
  }

  RateLimitFeedback feedback() {
    return feedback;
  }

  boolean uncertain() {
    return uncertain;
  }
}
