package org.knowm.xchange.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Dispatch;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Reason;

/** AC7 (shared cooldown), AC8 (missing/conflicting feedback) and AC14 (compatible outcomes). */
public class RateLimitFeedbackTest {

  private static final long SEC = 1_000_000_000L;

  private RateLimitFixture fixture;

  @After
  public void tearDown() {
    if (fixture != null) {
      fixture.cleanup();
    }
  }

  /** Plenty of capacity: any delay seen is caused by feedback alone. */
  private static RateLimitPolicy roomy() {
    return RateLimitFixture.policy(
            "t.feedback",
            List.of(
                RateLimitBudget.tokenBucket("user", ScopeKind.USER, 100, 100, Duration.ofSeconds(1)),
                RateLimitBudget.tokenBucket("ip", ScopeKind.EGRESS, 100, 100, Duration.ofSeconds(1))),
            RateLimitOperation.unitCost("read", RateLimitPriority.EXECUTION, true, "user"),
            RateLimitOperation.unitCost("other", RateLimitPriority.EXECUTION, true, "user"),
            RateLimitOperation.unitCost("publicRead", RateLimitPriority.EXECUTION, true, "ip"),
            RateLimitOperation.unitCost("write", RateLimitPriority.EXECUTION, false, "user"))
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofHours(1))
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(4), 0.0);
  }

  private RateLimitAttempt<String> reply(
      List<Long> sends, AtomicInteger counter, int[] statuses, String retryAfter) {
    return observer -> {
      int call = counter.getAndIncrement();
      synchronized (sends) {
        sends.add(fixture.now());
      }
      int status = statuses[Math.min(call, statuses.length - 1)];
      observer.observeHttpResponse(status, name -> "Retry-After".equalsIgnoreCase(name) ? retryAfter : null);
      if (status >= 400) {
        throw new IllegalStateException("http " + status);
      }
      return "ok";
    };
  }

  private String exec(RateLimitPolicy policy, String op, String scope, RateLimitAttempt<String> attempt)
      throws Exception {
    return fixture.context.execute(policy, RateLimitFixture.request(op), scope, attempt);
  }

  // ---- AC7 ------------------------------------------------------------------------------

  @Test
  public void retryAfterSecondsBlocksEverySharerUntilTheResetAndOthersContinue() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    List<Long> sends = new ArrayList<>();
    assertThatThrownBy(() -> exec(policy, "write", "u1", reply(sends, new AtomicInteger(), new int[] {429}, "10")))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.REMOTE_PRESSURE_EXHAUSTED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.REJECTED);
            });
    assertThat(sends).containsExactly(0L);

    // another user's allocation and the egress budget are unaffected
    List<Long> unaffected = new ArrayList<>();
    exec(policy, "read", "u2", reply(unaffected, new AtomicInteger(), new int[] {200}, null));
    exec(policy, "publicRead", "u1", reply(unaffected, new AtomicInteger(), new int[] {200}, null));
    assertThat(unaffected).containsExactly(0L, 0L);

    // every sharer of u1's budget (another operation, another exchange on the same context)
    List<Long> shared = new ArrayList<>();
    exec(policy, "other", "u1", reply(shared, new AtomicInteger(), new int[] {200}, null));
    assertThat(shared).containsExactly(10 * SEC);
  }

  @Test
  public void httpDateRetryAfterIsEvaluatedAgainstTheInjectedWallClock() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    // the fake wall clock starts at 2026-01-01T00:00:00Z, a Thursday
    List<Long> sends = new ArrayList<>();
    exec(
        policy,
        "read",
        "u1",
        reply(sends, new AtomicInteger(), new int[] {429, 200}, "Thu, 01 Jan 2026 00:00:07 GMT"));
    assertThat(sends).containsExactly(0L, 7 * SEC);

    // a date that is already in the past is not a usable reset: capped backoff applies instead
    List<Long> past = new ArrayList<>();
    long base = fixture.now();
    exec(
        policy,
        "read",
        "u1",
        reply(past, new AtomicInteger(), new int[] {429, 200}, "Wed, 31 Dec 2025 23:59:00 GMT"));
    assertThat(past).containsExactly(base, base + SEC);
  }

  @Test
  public void aBlockedSharerNeverDispatchesBeforeTheResetEvenWhileItIsParked() throws Exception {
    fixture = RateLimitFixture.blocking();
    RateLimitPolicy policy = roomy();
    List<Long> sends = new ArrayList<>();
    assertThatThrownBy(() -> exec(policy, "write", "u1", reply(sends, new AtomicInteger(), new int[] {429}, "10")))
        .isInstanceOf(RateLimitTerminatedException.class);
    RateLimitFixture.Task<String> sharer =
        fixture.start(
            () -> exec(policy, "other", "u1", reply(sends, new AtomicInteger(), new int[] {200}, null)));
    fixture.awaitQuiescent();
    fixture.advanceNanos(10 * SEC - 1);
    fixture.awaitQuiescent();
    assertThat(sharer.isDone()).isFalse();
    assertThat(sends).containsExactly(0L);
    fixture.advanceNanos(1);
    assertThat(sharer.result()).isEqualTo("ok");
    assertThat(sends).containsExactly(0L, 10 * SEC);
  }

  @Test
  public void aResetBeyondTheDeadlineTerminatesWithoutAnEarlyOrSecondAttempt() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy().withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5));
    List<Long> sends = new ArrayList<>();
    assertThatThrownBy(() -> exec(policy, "read", "u1", reply(sends, new AtomicInteger(), new int[] {429, 200}, "60")))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.REMOTE_PRESSURE_EXHAUSTED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.REJECTED);
            });
    assertThat(sends).containsExactly(0L);
    assertThat(fixture.now()).isZero(); // it did not wait for a reset it could never use
    // the cooldown is still recorded for the sharers, who cannot be served inside their deadline
    assertThatThrownBy(() -> exec(policy, "other", "u1", reply(sends, new AtomicInteger(), new int[] {200}, null)))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.DEADLINE_EXCEEDED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.NOT_SENT);
            });
    assertThat(sends).containsExactly(0L);
  }

  @Test
  public void aCallerDeadlineScopeShorterThanTheResetTerminatesTheReplay() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    List<Long> sends = new ArrayList<>();
    // RateLimitDeadline runs on the real nano clock; a one-hour allowance stays far from expiry
    assertThat(
            RateLimitDeadline.call(
                Duration.ofHours(1),
                () -> exec(policy, "read", "u1", reply(sends, new AtomicInteger(), new int[] {429, 200}, "3"))))
        .isEqualTo("ok");
    assertThat(sends).containsExactly(0L, 3 * SEC);
    assertThatThrownBy(
            () ->
                RateLimitDeadline.call(
                    Duration.ZERO,
                    () -> exec(policy, "read", "u1", reply(sends, new AtomicInteger(), new int[] {200}, null))))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.DEADLINE_EXCEEDED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.NOT_SENT);
            });
    assertThat(sends).hasSize(2);
  }

  // ---- AC8 ------------------------------------------------------------------------------

  @Test
  public void missingOrMalformedResetUsesCappedBackoffAndBoundedAttempts() throws Exception {
    for (String malformed : new String[] {null, "", "soon", "-5", "0", "1.5", "Mon, 99 Foo 2026 00:00:00 GMT"}) {
      fixture = RateLimitFixture.autoAdvance();
      RateLimitPolicy policy = roomy().withMaxAttempts(5);
      List<Long> sends = new ArrayList<>();
      assertThatThrownBy(() -> exec(policy, "read", "u1", reply(sends, new AtomicInteger(), new int[] {429}, malformed)))
          .as("retry-after=%s", malformed)
          .isInstanceOfSatisfying(
              RateLimitTerminatedException.class,
              e -> {
                assertThat(e.getReason()).isEqualTo(Reason.REMOTE_PRESSURE_EXHAUSTED);
                assertThat(e.getDispatch()).isEqualTo(Dispatch.REJECTED);
              });
      // delays 1s, 2s, 4s, then capped at 4s; exactly maxAttempts sends, never more
      assertThat(sends).as("retry-after=%s", malformed).containsExactly(0L, SEC, 3 * SEC, 7 * SEC, 11 * SEC);
      fixture.cleanup();
    }
  }

  @Test
  public void jitterOnlyEverShortensTheBackoffAndNeverBelowItsConfiguredFloor() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    // fixture jitter source is the constant 0.5; fraction 0.25 shortens each delay by 12.5%
    RateLimitPolicy policy =
        roomy()
            .withFallbackBackoff(Duration.ofSeconds(8), Duration.ofSeconds(8), 0.25)
            .withMaxAttempts(2);
    List<Long> sends = new ArrayList<>();
    assertThatThrownBy(() -> exec(policy, "read", "u1", reply(sends, new AtomicInteger(), new int[] {429}, null)))
        .isInstanceOf(RateLimitTerminatedException.class);
    assertThat(sends).containsExactly(0L, 7 * SEC);
  }

  @Test
  public void anExchangeBanIsNeverReplayedAndCoolsTheBudgetForTheCap() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    List<Long> sends = new ArrayList<>();
    assertThatThrownBy(() -> exec(policy, "read", "u1", reply(sends, new AtomicInteger(), new int[] {418}, null)))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.REMOTE_PRESSURE_EXHAUSTED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.REJECTED);
              assertThat(e.getMessage()).contains("banned");
            });
    assertThat(sends).containsExactly(0L); // one attempt although the operation is replay-safe
    List<Long> after = new ArrayList<>();
    exec(policy, "other", "u1", reply(after, new AtomicInteger(), new int[] {200}, null));
    assertThat(after).containsExactly(4 * SEC); // no Retry-After: the policy's backoff cap
  }

  @Test
  public void aBanWithRetryAfterCoolsForTheReportedDuration() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    List<Long> sends = new ArrayList<>();
    assertThatThrownBy(() -> exec(policy, "read", "u1", reply(sends, new AtomicInteger(), new int[] {418}, "9")))
        .isInstanceOf(RateLimitTerminatedException.class);
    List<Long> after = new ArrayList<>();
    exec(policy, "other", "u1", reply(after, new AtomicInteger(), new int[] {200}, null));
    assertThat(after).containsExactly(9 * SEC);
  }

  @Test
  public void aStaleShorterRejectionCannotShortenAnAlreadyLongerCooldown() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    List<Long> sends = new ArrayList<>();
    // An in-flight request on the same budget reports a 10s reset, then this (older) request
    // answers with a shorter 1s reset: the cooldown must stay at 10s.
    assertThatThrownBy(
            () ->
                exec(
                    policy,
                    "write",
                    "u1",
                    observer -> {
                      assertThatThrownBy(
                              () ->
                                  exec(
                                      policy,
                                      "write",
                                      "u1",
                                      reply(sends, new AtomicInteger(), new int[] {429}, "10")))
                          .isInstanceOf(RateLimitTerminatedException.class);
                      observer.observeHttpResponse(429, name -> "Retry-After".equals(name) ? "1" : null);
                      throw new IllegalStateException("stale 429");
                    }))
        .isInstanceOf(RateLimitTerminatedException.class);
    List<Long> after = new ArrayList<>();
    exec(policy, "read", "u1", reply(after, new AtomicInteger(), new int[] {200}, null));
    assertThat(after).containsExactly(10 * SEC);
  }

  @Test
  public void successfulResponsesWithRemainingCreditHeadersNeverMintCapacity() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy =
        RateLimitFixture.policy(
                "t.nomint",
                List.of(RateLimitBudget.tokenBucket("one", ScopeKind.USER, 1, 1, Duration.ofHours(1))),
                RateLimitOperation.unitCost("read", RateLimitPriority.EXECUTION, true, "one"))
            .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofDays(1));
    List<Long> sends = new ArrayList<>();
    RateLimitAttempt<String> generous =
        observer -> {
          synchronized (sends) {
            sends.add(fixture.now());
          }
          observer.observeHttpResponse(
              200, name -> "X-RateLimit-Remaining".equalsIgnoreCase(name) ? "100000" : null);
          return "ok";
        };
    exec(policy, "read", "u1", generous);
    exec(policy, "read", "u1", generous);
    assertThat(sends).containsExactly(0L, 3600 * SEC);
  }

  @Test
  public void anUncertainOutcomeIsNeverReplayedEvenForAReplaySafeOperation() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    List<Long> sends = new ArrayList<>();
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(
            () ->
                exec(
                    policy,
                    "read",
                    "u1",
                    observer -> {
                      calls.incrementAndGet();
                      synchronized (sends) {
                        sends.add(fixture.now());
                      }
                      observer.markUncertain();
                      observer.observeFeedback(RateLimitFeedback.rejected(Duration.ofSeconds(2)));
                      throw new IllegalStateException("connection reset");
                    }))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.REMOTE_PRESSURE_EXHAUSTED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.UNCERTAIN);
              assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
            });
    assertThat(calls.get()).isEqualTo(1);
    assertThat(sends).containsExactly(0L);
  }

  @Test
  public void feedbackThatIsNotDeclaredByThePolicyIsNotRatePressure() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    // 503 is not declared by the fixture policy: the failure propagates untouched, no replay
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(() -> exec(policy, "read", "u1", reply(new ArrayList<>(), calls, new int[] {503}, "5")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("http 503");
    assertThat(calls.get()).isEqualTo(1);
    assertThat(fixture.context.diagnostics().getRatePressureEvents()).isZero();
  }

  @Test
  public void retryAfterParsingAcceptsDeltaSecondsAndAllHttpDateFormats() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    assertThat(RateLimitFeedback.parseRetryAfter("120", now)).isEqualTo(Duration.ofSeconds(120));
    assertThat(RateLimitFeedback.parseRetryAfter(" 7 ", now)).isEqualTo(Duration.ofSeconds(7));
    assertThat(RateLimitFeedback.parseRetryAfter("Thu, 01 Jan 2026 00:01:00 GMT", now))
        .isEqualTo(Duration.ofMinutes(1));
    assertThat(RateLimitFeedback.parseRetryAfter("Thursday, 01-Jan-26 00:00:30 GMT", now))
        .isEqualTo(Duration.ofSeconds(30));
    assertThat(RateLimitFeedback.parseRetryAfter("Thu Jan  1 00:00:15 2026", now))
        .isEqualTo(Duration.ofSeconds(15));
    assertThat(RateLimitFeedback.parseRetryAfter("Thu, 01 Jan 2026 00:00:00 GMT", now)).isNull();
    assertThat(RateLimitFeedback.parseRetryAfter("99999999999999999999", now)).isNull();
    assertThat(RateLimitFeedback.parseRetryAfter(null, now)).isNull();
  }

  // ---- AC14 -----------------------------------------------------------------------------

  @Test
  public void existingRateLimitExceededCatchesStillWorkAndExposeTheTypedOutcome() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    String secretScope = "api-key-owner-4711";
    try {
      exec(policy, "write", secretScope, reply(new ArrayList<>(), new AtomicInteger(), new int[] {429}, "2"));
      throw new AssertionError("expected a terminal rate-limit outcome");
    } catch (RateLimitExceededException e) {
      assertThat(e).isInstanceOf(RateLimitTerminatedException.class);
      assertThat(((RateLimitTerminatedException) e).getReason())
          .isEqualTo(Reason.REMOTE_PRESSURE_EXHAUSTED);
      assertThat(e.getMessage()).doesNotContain(secretScope);
      assertThat(e.toString()).doesNotContain(secretScope);
    }
  }

  @Test
  public void diagnosticsSeparateWaitingRetryRejectionAndNoDispatchWithoutLeakingScopes()
      throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    String secretScope = "api-key-owner-4711";
    // one retry after a 2s reset, then success
    exec(policy, "read", secretScope, reply(new ArrayList<>(), new AtomicInteger(), new int[] {429, 200}, "2"));
    // one terminal confirmed rejection of a write
    assertThatThrownBy(
            () -> exec(policy, "write", secretScope, reply(new ArrayList<>(), new AtomicInteger(), new int[] {429}, "1")))
        .isInstanceOf(RateLimitTerminatedException.class);
    // one no-dispatch termination
    assertThatThrownBy(
            () -> exec(policy, "unknown", secretScope, reply(new ArrayList<>(), new AtomicInteger(), new int[] {200}, null)))
        .isInstanceOf(RateLimitTerminatedException.class);

    RateLimitDiagnostics diagnostics = fixture.context.diagnostics();
    assertThat(diagnostics.getRetries()).isEqualTo(1);
    assertThat(diagnostics.getRatePressureEvents()).isEqualTo(2);
    assertThat(diagnostics.getAdmissions()).isEqualTo(3); // read, read replay, write
    assertThat(diagnostics.getTotalWaitNanos()).isGreaterThanOrEqualTo(2 * SEC); // the replay waited
    assertThat(diagnostics.getTerminations().get(Reason.REMOTE_PRESSURE_EXHAUSTED)).isEqualTo(1L);
    assertThat(diagnostics.getTerminations().get(Reason.UNCLASSIFIED_OPERATION)).isEqualTo(1L);
    assertThat(diagnostics.getTerminations().get(Reason.QUEUE_SATURATED)).isZero();
    assertThat(diagnostics.getPolicyVersions()).containsEntry("t.feedback", "test-1");
    assertThat(diagnostics.getTrackedScopeCount()).isPositive();
    assertThat(diagnostics.toString()).doesNotContain(secretScope);
    assertThat(fixture.context.toString()).doesNotContain(secretScope);
  }

  @Test
  public void failuresThatAreNotRatePressureReachTheCallerUnchanged() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = roomy();
    IllegalStateException boom = new IllegalStateException("boom");
    assertThatThrownBy(
            () ->
                exec(
                    policy,
                    "read",
                    "u1",
                    observer -> {
                      observer.observeHttpResponse(500, name -> null);
                      throw boom;
                    }))
        .isSameAs(boom);
    assertThat(fixture.context.diagnostics().getRatePressureEvents()).isZero();
  }
}
