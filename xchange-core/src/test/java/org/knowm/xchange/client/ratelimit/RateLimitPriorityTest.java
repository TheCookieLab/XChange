package org.knowm.xchange.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Dispatch;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Reason;

/** AC5 (execution priority, FIFO, isolation) and AC6 (bounded queues, distinct outcomes). */
public class RateLimitPriorityTest {

  private static final long SEC = 1_000_000_000L;

  private RateLimitFixture fixture;

  @After
  public void tearDown() {
    if (fixture != null) {
      fixture.cleanup();
    }
  }

  private static RateLimitPolicy sharedPolicy() {
    return RateLimitFixture.policy(
            "t.priority",
            List.of(
                RateLimitBudget.tokenBucket("shared", ScopeKind.USER, 1, 1, Duration.ofSeconds(1))),
            RateLimitOperation.unitCost("md", RateLimitPriority.MARKET_DATA, true, "shared"),
            RateLimitOperation.unitCost("exec", RateLimitPriority.EXECUTION, true, "shared"))
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofHours(1))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofHours(1));
  }

  private RateLimitFixture.Task<String> startOp(
      RateLimitPolicy policy, String operation, String scope, List<String> order, String label) {
    return fixture.start(
        () ->
            fixture.context.execute(
                policy,
                RateLimitFixture.request(operation),
                scope,
                observer -> {
                  synchronized (order) {
                    order.add(label + "@" + fixture.now());
                  }
                  return label;
                }));
  }

  // ---- AC5 ------------------------------------------------------------------------------

  @Test
  public void executionObtainsTheNextSharedPermitBeforeEightyTwoQueuedMarketDataWaiters()
      throws Exception {
    fixture = RateLimitFixture.blocking();
    RateLimitPolicy policy = sharedPolicy();
    List<Long> first = new ArrayList<>();
    fixture.run(policy, "md", "u1", first); // consumes the only token at time zero
    assertThat(first).containsExactly(0L);

    int queued = 82;
    List<String> order = new ArrayList<>();
    for (int i = 0; i < queued; i++) {
      startOp(policy, "md", "u1", order, "md" + i);
      fixture.awaitQuiescent();
    }
    startOp(policy, "exec", "u1", order, "exec");
    fixture.awaitQuiescent();
    assertThat(order).isEmpty();

    // An unrelated scope (another user) is served immediately while 83 waiters are parked.
    List<Long> unrelated = new ArrayList<>();
    fixture.run(policy, "md", "u2", unrelated);
    assertThat(unrelated).containsExactly(0L);

    for (int i = 0; i <= queued; i++) {
      fixture.advanceNanos(SEC);
      fixture.awaitQuiescent();
      assertThat(order).hasSize(i + 1);
    }
    List<String> expected = new ArrayList<>();
    expected.add("exec@" + SEC);
    for (int i = 0; i < queued; i++) {
      expected.add("md" + i + "@" + (i + 2) * SEC);
    }
    assertThat(order).containsExactlyElementsOf(expected);
  }

  @Test
  public void waitersOfOneClassAreServedInArrivalOrderAndExecutionStillPrecedesEarlierMarketData()
      throws Exception {
    fixture = RateLimitFixture.blocking();
    RateLimitPolicy policy = sharedPolicy();
    fixture.run(policy, "md", "u", new ArrayList<>());
    List<String> order = new ArrayList<>();
    startOp(policy, "md", "u", order, "m0");
    fixture.awaitQuiescent();
    startOp(policy, "exec", "u", order, "e0");
    fixture.awaitQuiescent();
    startOp(policy, "md", "u", order, "m1");
    fixture.awaitQuiescent();
    startOp(policy, "exec", "u", order, "e1");
    fixture.awaitQuiescent();
    startOp(policy, "exec", "u", order, "e2");
    fixture.awaitQuiescent();
    for (int i = 0; i < 5; i++) {
      fixture.advanceNanos(SEC);
      fixture.awaitQuiescent();
    }
    assertThat(order)
        .containsExactly(
            "e0@" + SEC, "e1@" + 2 * SEC, "e2@" + 3 * SEC, "m0@" + 4 * SEC, "m1@" + 5 * SEC);
  }

  @Test
  public void aWaiterNeedingTwoBudgetsDoesNotLetALaterWaiterOvertakeItOnTheSharedOne()
      throws Exception {
    fixture = RateLimitFixture.blocking();
    RateLimitPolicy policy =
        RateLimitFixture.policy(
                "t.heavy",
                List.of(
                    RateLimitBudget.tokenBucket("a", ScopeKind.EGRESS, 2, 2, Duration.ofSeconds(2)),
                    RateLimitBudget.tokenBucket("b", ScopeKind.EGRESS, 1, 1, Duration.ofSeconds(10))),
                new RateLimitOperation("heavy", RateLimitPriority.EXECUTION, Map.of("a", 2L, "b", 1L), true),
                RateLimitOperation.unitCost("light", RateLimitPriority.EXECUTION, true, "a"))
            .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofHours(1));
    fixture.run(policy, "heavy", "u", new ArrayList<>()); // empties a and b at t=0
    List<String> order = new ArrayList<>();
    startOp(policy, "heavy", "u", order, "heavy");
    fixture.awaitQuiescent();
    startOp(policy, "light", "u", order, "light");
    fixture.awaitQuiescent();
    // `a` alone would pay for `light` at 1s but the older heavy request is entitled to it first.
    fixture.advanceNanos(2 * SEC);
    fixture.awaitQuiescent();
    assertThat(order).isEmpty();
    fixture.advanceNanos(8 * SEC);
    fixture.awaitQuiescent();
    assertThat(order).containsExactly("heavy@" + 10 * SEC);
    fixture.advanceNanos(SEC); // heavy emptied `a`; one token is back after a second
    fixture.awaitQuiescent();
    assertThat(order).containsExactly("heavy@" + 10 * SEC, "light@" + 11 * SEC);
  }

  // ---- AC6 ------------------------------------------------------------------------------

  @Test
  public void twoHundredFiftySixMarketDataWaitersAreAcceptedWithoutConsumingTheExecutionQueue()
      throws Exception {
    fixture = RateLimitFixture.blocking();
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.queues",
            List.of(
                RateLimitBudget.tokenBucket("md", ScopeKind.EGRESS, 1, 1, Duration.ofHours(1)),
                RateLimitBudget.tokenBucket("exec", ScopeKind.EGRESS, 1000, 1000, Duration.ofSeconds(1))),
            RateLimitOperation.unitCost("md", RateLimitPriority.MARKET_DATA, true, "md"),
            RateLimitOperation.unitCost("exec", RateLimitPriority.EXECUTION, true, "exec"))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofDays(1));
    assertThat(policy.pendingLimit(RateLimitPriority.MARKET_DATA)).isGreaterThanOrEqualTo(256);
    fixture.run(policy, "md", "u", new ArrayList<>());
    AtomicInteger sent = new AtomicInteger();
    List<RateLimitFixture.Task<String>> waiters = new ArrayList<>();
    for (int i = 0; i < 256; i++) {
      waiters.add(
          fixture.start(
              () ->
                  fixture.context.execute(
                      policy,
                      RateLimitFixture.request("md"),
                      "u",
                      observer -> {
                        sent.incrementAndGet();
                        return "late";
                      })));
      fixture.awaitQuiescent();
    }
    assertThat(fixture.context.diagnostics().getPending())
        .containsEntry(RateLimitPriority.MARKET_DATA, 256)
        .containsEntry(RateLimitPriority.EXECUTION, 0);

    // The 257th market-data operation is rejected immediately, nothing sent, nothing waited.
    assertThatThrownBy(() -> fixture.run(policy, "md", "u", new ArrayList<>()))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.QUEUE_SATURATED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.NOT_SENT);
            });
    assertThat(fixture.now()).isZero();

    // Execution is unaffected by the full market-data queue.
    List<Long> exec = new ArrayList<>();
    fixture.run(policy, "exec", "u", exec);
    assertThat(exec).containsExactly(0L);

    fixture.context.close();
    for (RateLimitFixture.Task<String> waiter : waiters) {
      assertThat(waiter.terminated().getReason()).isEqualTo(Reason.SHUTDOWN);
    }
    assertThat(sent.get()).isZero();
    assertThat(fixture.context.diagnostics().getPending())
        .containsEntry(RateLimitPriority.MARKET_DATA, 0)
        .containsEntry(RateLimitPriority.EXECUTION, 0);
  }

  @Test
  public void aFullExecutionQueueSaturatesOnlyExecutionAndLeavesMarketDataOpen() throws Exception {
    fixture = RateLimitFixture.blocking();
    RateLimitPolicy policy =
        RateLimitFixture.policy(
                "t.queues2",
                List.of(
                    RateLimitBudget.tokenBucket("slow", ScopeKind.EGRESS, 1, 1, Duration.ofHours(1)),
                    RateLimitBudget.tokenBucket("free", ScopeKind.EGRESS, 100, 100, Duration.ofSeconds(1))),
                RateLimitOperation.unitCost("exec", RateLimitPriority.EXECUTION, true, "slow"),
                RateLimitOperation.unitCost("md", RateLimitPriority.MARKET_DATA, true, "free"))
            .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofDays(1))
            .withPendingLimit(RateLimitPriority.EXECUTION, 2);
    fixture.run(policy, "exec", "u", new ArrayList<>());
    List<String> order = new ArrayList<>();
    startOp(policy, "exec", "u", order, "e0");
    fixture.awaitQuiescent();
    startOp(policy, "exec", "u", order, "e1");
    fixture.awaitQuiescent();
    assertThatThrownBy(() -> fixture.run(policy, "exec", "u", new ArrayList<>()))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.QUEUE_SATURATED));
    List<Long> md = new ArrayList<>();
    fixture.run(policy, "md", "u", md);
    assertThat(md).containsExactly(0L);
    assertThat(fixture.context.diagnostics().getPending())
        .containsEntry(RateLimitPriority.EXECUTION, 2)
        .containsEntry(RateLimitPriority.MARKET_DATA, 0);
  }

  @Test
  public void everyTerminalReasonIsDistinctNothingIsSentLateAndNoWaiterLeaks() throws Exception {
    fixture = RateLimitFixture.blocking();
    // one token every 4s; execution may wait 5s (the default) and keeps two waiters
    RateLimitPolicy policy =
        RateLimitFixture.policy(
                "t.reasons",
                List.of(
                    RateLimitBudget.tokenBucket("slow", ScopeKind.EGRESS, 1, 1, Duration.ofSeconds(4)),
                    RateLimitBudget.tokenBucket("free", ScopeKind.EGRESS, 100, 100, Duration.ofSeconds(1))),
                RateLimitOperation.unitCost("slowExec", RateLimitPriority.EXECUTION, true, "slow"),
                new RateLimitOperation("huge", RateLimitPriority.EXECUTION, Map.of("slow", 5L), true),
                RateLimitOperation.unitCost("write", RateLimitPriority.EXECUTION, false, "free"))
            .withPendingLimit(RateLimitPriority.EXECUTION, 2);
    assertThat(policy.maxWait(RateLimitPriority.EXECUTION)).isEqualTo(Duration.ofSeconds(5));
    AtomicInteger sent = new AtomicInteger();
    Set<Reason> seen = EnumSet.noneOf(Reason.class);
    Map<String, Dispatch> dispatches = new java.util.LinkedHashMap<>();

    // the only slow token goes to an immediate dispatch at t=0
    fixture.context.execute(
        policy, RateLimitFixture.request("slowExec"), "u", o -> sent.incrementAndGet());

    record(seen, dispatches, "impossible", expectTerminated(policy, "huge", sent));
    record(seen, dispatches, "unclassified", expectTerminated(policy, "nope", sent));

    RateLimitFixture.Task<Integer> toDispatch = waiter(policy, sent);
    fixture.awaitQuiescent();
    RateLimitFixture.Task<Integer> toCancel = waiter(policy, sent);
    fixture.awaitQuiescent();
    record(seen, dispatches, "saturated", expectTerminated(policy, "slowExec", sent));
    toCancel.thread.interrupt();
    record(seen, dispatches, "cancelled", toCancel.terminated());

    // queued behind toDispatch; once that one takes the token at 4s the next is due at 8s > 5s
    RateLimitFixture.Task<Integer> toExpire = waiter(policy, sent);
    fixture.awaitQuiescent();
    fixture.advanceNanos(4 * SEC - 1);
    fixture.awaitQuiescent();
    assertThat(toDispatch.isDone()).isFalse();
    assertThat(toExpire.isDone()).isFalse();
    assertThat(sent.get()).isEqualTo(1);
    fixture.advanceNanos(1);
    assertThat(toDispatch.result()).isEqualTo(2);
    record(seen, dispatches, "deadline", toExpire.terminated());
    assertThat(sent.get()).isEqualTo(2);

    // a confirmed 429 on a request that is not replay-safe: one attempt, then terminal
    assertThatThrownBy(
            () ->
                fixture.context.execute(
                    policy,
                    RateLimitFixture.request("write"),
                    "u",
                    observer -> {
                      sent.incrementAndGet();
                      observer.observeHttpResponse(429, name -> null);
                      throw new IllegalStateException("429");
                    }))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class, e -> record(seen, dispatches, "pressure", e));

    RateLimitFixture.Task<Integer> shutdown = waiter(policy, sent);
    fixture.awaitQuiescent();
    fixture.context.close();
    record(seen, dispatches, "shutdown", shutdown.terminated());
    assertThatThrownBy(() -> fixture.run(policy, "write", "u", new ArrayList<>()))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.SHUTDOWN));

    assertThat(seen)
        .containsExactlyInAnyOrder(
            Reason.IMPOSSIBLE_COST,
            Reason.UNCLASSIFIED_OPERATION,
            Reason.QUEUE_SATURATED,
            Reason.CANCELLED,
            Reason.DEADLINE_EXCEEDED,
            Reason.REMOTE_PRESSURE_EXHAUSTED,
            Reason.SHUTDOWN);
    assertThat(dispatches)
        .containsEntry("impossible", Dispatch.NOT_SENT)
        .containsEntry("unclassified", Dispatch.NOT_SENT)
        .containsEntry("saturated", Dispatch.NOT_SENT)
        .containsEntry("cancelled", Dispatch.NOT_SENT)
        .containsEntry("deadline", Dispatch.NOT_SENT)
        .containsEntry("shutdown", Dispatch.NOT_SENT)
        .containsEntry("pressure", Dispatch.REJECTED);
    // the t=0 dispatch, the 4s dispatch and the rejected write; nothing else was ever sent
    assertThat(sent.get()).isEqualTo(3);
    RateLimitDiagnostics diagnostics = fixture.context.diagnostics();
    assertThat(diagnostics.getPending())
        .containsEntry(RateLimitPriority.EXECUTION, 0)
        .containsEntry(RateLimitPriority.MARKET_DATA, 0);
    for (Reason reason : seen) {
      assertThat(diagnostics.getTerminations().get(reason))
          .as(reason.name())
          .isGreaterThanOrEqualTo(1L);
    }
  }

  private RateLimitFixture.Task<Integer> waiter(RateLimitPolicy policy, AtomicInteger sent) {
    return fixture.start(
        () ->
            fixture.context.execute(
                policy,
                RateLimitFixture.request("slowExec"),
                "u",
                observer -> sent.incrementAndGet()));
  }

  private RateLimitTerminatedException expectTerminated(
      RateLimitPolicy policy, String operation, AtomicInteger sent) {
    try {
      fixture.context.execute(
          policy, RateLimitFixture.request(operation), "u", observer -> sent.incrementAndGet());
    } catch (RateLimitTerminatedException e) {
      return e;
    } catch (Exception e) {
      throw new AssertionError("unexpected " + e, e);
    }
    throw new AssertionError("operation " + operation + " was admitted");
  }

  private static void record(
      Set<Reason> seen,
      Map<String, Dispatch> dispatches,
      String label,
      RateLimitTerminatedException e) {
    seen.add(e.getReason());
    dispatches.put(label, e.getDispatch());
  }
}
