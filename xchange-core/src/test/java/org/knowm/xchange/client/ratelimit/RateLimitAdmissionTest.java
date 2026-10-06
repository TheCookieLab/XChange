package org.knowm.xchange.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;

/** AC1 (atomic weighted admission) and AC2 (rate-law semantics) against independent oracles. */
public class RateLimitAdmissionTest {

  private static final long SEC = 1_000_000_000L;
  private static final String SCOPE = "u";

  private RateLimitFixture fixture;

  @After
  public void tearDown() {
    if (fixture != null) {
      fixture.cleanup();
    }
  }

  // ---- AC2 ------------------------------------------------------------------------------

  @Test
  public void tokenBucketCapacityTwoRefillTwoPerSecondAdmitsAtZeroZeroHalfOneOneAndHalf()
      throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.bucket",
            List.of(
                RateLimitBudget.tokenBucket("b", ScopeKind.EGRESS, 2, 2, Duration.ofSeconds(1))),
            RateLimitOperation.unitCost("op", RateLimitPriority.EXECUTION, true, "b"));
    List<Long> dispatches = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      fixture.run(policy, "op", SCOPE, dispatches);
    }
    // Independent closed form for unit costs submitted together: t_k = max(0, (k - C + 1) / r).
    assertThat(dispatches)
        .containsExactly(0L, 0L, SEC / 2, SEC, SEC + SEC / 2);
  }

  @Test
  public void tokenBucketWithAwkwardRateNeverAdmitsEarlyAndRoundsUpByLessThanOneNanosecond()
      throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    // capacity 3, refill 3 tokens per 2 seconds: t_k = (k - 2) * 2s / 3 exactly (not whole nanos)
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.bucket.awkward",
            List.of(
                RateLimitBudget.tokenBucket("b", ScopeKind.EGRESS, 3, 3, Duration.ofSeconds(2))),
            RateLimitOperation.unitCost("op", RateLimitPriority.EXECUTION, true, "b"));
    List<Long> dispatches = new ArrayList<>();
    int requests = 12;
    for (int i = 0; i < requests; i++) {
      fixture.run(policy, "op", SCOPE, dispatches);
    }
    for (int k = 0; k < requests; k++) {
      long exactTimes3 = Math.max(0L, (k - 2L) * 2L * SEC); // exact time is this / 3
      long actual = dispatches.get(k);
      assertThat(actual * 3).as("request %d not early", k).isGreaterThanOrEqualTo(exactTimes3);
      assertThat(actual * 3 - exactTimes3)
          .as("request %d at most one nanosecond late", k)
          .isLessThan(3L);
    }
  }

  @Test
  public void tokenBucketMatchesOracleForWeightedCostsAndIrregularSubmissionTimes()
      throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    long capacity = 5;
    long refill = 3;
    long period = 2 * SEC;
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.bucket.weighted",
            List.of(
                RateLimitBudget.tokenBucket(
                    "b", ScopeKind.EGRESS, capacity, refill, Duration.ofNanos(period))),
            RateLimitOperation.unitCost("c1", RateLimitPriority.EXECUTION, true, "b"),
            new RateLimitOperation(
                "c4", RateLimitPriority.EXECUTION, Map.of("b", 4L), true),
            new RateLimitOperation(
                "c5", RateLimitPriority.EXECUTION, Map.of("b", 5L), true));
    String[] ops = {"c1", "c4", "c1", "c5", "c1", "c1", "c4", "c4", "c1", "c5", "c1"};
    long[] costs = {1, 4, 1, 5, 1, 1, 4, 4, 1, 5, 1};
    long[] submit = {0, 0, 300_000_000L, 300_000_000L, 5 * SEC, 5 * SEC, 5 * SEC, 6 * SEC, 6 * SEC, 6 * SEC, 30 * SEC};

    long[] expected = bucketOracle(capacity, refill, period, submit, costs);
    long[] actual = new long[ops.length];
    for (int i = 0; i < ops.length; i++) {
      long wait = submit[i] - fixture.now();
      if (wait > 0) {
        fixture.advanceNanos(wait);
      }
      List<Long> one = new ArrayList<>();
      fixture.run(policy, ops[i], SCOPE, one);
      actual[i] = one.get(0);
    }
    assertThat(actual).containsExactly(expected);
  }

  @Test
  public void fixedWindowAdmitsLimitPerAlignedWindowAndReleasesAtTheNextBoundary()
      throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    long window = 2 * SEC;
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.fixed",
            List.of(
                RateLimitBudget.fixedWindow(
                    "b", ScopeKind.EGRESS, 3, Duration.ofNanos(window))),
            RateLimitOperation.unitCost("c1", RateLimitPriority.EXECUTION, true, "b"),
            new RateLimitOperation("c2", RateLimitPriority.EXECUTION, Map.of("b", 2L), true));
    // Start mid-window so the first boundary is not at the submission time.
    fixture.advanceNanos(SEC / 2);
    String[] ops = {"c1", "c1", "c1", "c1", "c2", "c1", "c1", "c2", "c1"};
    long[] costs = {1, 1, 1, 1, 2, 1, 1, 2, 1};
    long[] submit = new long[ops.length];
    java.util.Arrays.fill(submit, SEC / 2);
    submit[8] = 20 * SEC + 1;

    long[] expected = fixedOracle(3, window, submit, costs);
    long[] actual = new long[ops.length];
    for (int i = 0; i < ops.length; i++) {
      List<Long> one = new ArrayList<>();
      long wait = submit[i] - fixture.now();
      if (wait > 0) {
        fixture.advanceNanos(wait);
      }
      fixture.run(policy, ops[i], SCOPE, one);
      actual[i] = one.get(0);
    }
    assertThat(actual).containsExactly(expected);
    // Boundary spot checks that do not depend on the oracle code: three in window 0, then 2s.
    assertThat(actual[0]).isEqualTo(SEC / 2);
    assertThat(actual[2]).isEqualTo(SEC / 2);
    assertThat(actual[3]).isEqualTo(window);
  }

  @Test
  public void rollingWindowReleasesEachSlotExactlyOneWindowAfterItsConsumption() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    long window = SEC;
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.rolling",
            List.of(
                RateLimitBudget.rollingWindow(
                    "b", ScopeKind.EGRESS, 3, Duration.ofNanos(window))),
            RateLimitOperation.unitCost("c1", RateLimitPriority.EXECUTION, true, "b"),
            new RateLimitOperation("c2", RateLimitPriority.EXECUTION, Map.of("b", 2L), true));
    String[] ops = {"c1", "c1", "c1", "c1", "c1", "c1", "c2", "c1", "c2", "c1", "c1"};
    long[] costs = {1, 1, 1, 1, 1, 1, 2, 1, 2, 1, 1};
    long[] submit = {
      0,
      200_000_000L,
      400_000_000L,
      500_000_000L,
      500_000_000L,
      500_000_000L,
      500_000_000L,
      500_000_000L,
      500_000_000L,
      9 * SEC,
      9 * SEC
    };
    long[] expected = rollingOracle(3, window, submit, costs);
    long[] actual = new long[ops.length];
    for (int i = 0; i < ops.length; i++) {
      long wait = submit[i] - fixture.now();
      if (wait > 0) {
        fixture.advanceNanos(wait);
      }
      List<Long> one = new ArrayList<>();
      fixture.run(policy, ops[i], SCOPE, one);
      actual[i] = one.get(0);
    }
    assertThat(actual).containsExactly(expected);
    // Hand-checked prefix: 0, 0.2, 0.4 immediately; the fourth waits for the first to age out.
    assertThat(actual[0]).isEqualTo(0L);
    assertThat(actual[3]).isEqualTo(SEC);
    assertThat(actual[4]).isEqualTo(SEC + 200_000_000L);
    assertThat(actual[5]).isEqualTo(SEC + 400_000_000L);
  }

  // ---- AC1 ------------------------------------------------------------------------------

  @Test
  public void blockedRequestDoesNotPartiallyConsumeAnotherBudgetSequentialOracle()
      throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    long capA = 2;
    long capB = 1;
    // A: 2 tokens, +1 per second. B: 1 token, +1 per 4 seconds.
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.atomic",
            List.of(
                RateLimitBudget.tokenBucket("a", ScopeKind.EGRESS, capA, 1, Duration.ofSeconds(1)),
                RateLimitBudget.tokenBucket("b", ScopeKind.EGRESS, capB, 1, Duration.ofSeconds(4))),
            new RateLimitOperation(
                "both", RateLimitPriority.EXECUTION, Map.of("a", 2L, "b", 1L), true),
            RateLimitOperation.unitCost("onlyA", RateLimitPriority.EXECUTION, true, "a"));
    String[] ops = {"both", "onlyA", "both", "onlyA", "both", "onlyA", "onlyA"};
    long[][] cost = {{2, 1}, {1, 0}, {2, 1}, {1, 0}, {2, 1}, {1, 0}, {1, 0}};
    long[] expected = twoBucketOracle(new long[] {capA, capB}, new long[] {1, 1}, new long[] {SEC, 4 * SEC}, cost);
    long[] actual = new long[ops.length];
    for (int i = 0; i < ops.length; i++) {
      List<Long> one = new ArrayList<>();
      fixture.run(policy, ops[i], SCOPE, one);
      actual[i] = one.get(0);
    }
    assertThat(actual).containsExactly(expected);
    // `both` #2 needs B (refills at 4s) although A could pay at 2s: A must not have been debited
    // while waiting, so `onlyA` right after is paid from A's full refill, not from a debited one.
    assertThat(actual[2]).isEqualTo(4 * SEC);
  }

  @Test
  public void concurrentWeightedRequestsNeverDispatchWhenAnyRequiredBudgetCannotPay()
      throws Exception {
    fixture = RateLimitFixture.blocking();
    long capA = 3;
    long refillA = 2;
    long periodA = SEC;
    long capB = 2;
    long refillB = 1;
    long periodB = SEC;
    RateLimitPolicy policy =
        RateLimitFixture.policy(
            "t.concurrent",
            List.of(
                RateLimitBudget.tokenBucket(
                    "a", ScopeKind.EGRESS, capA, refillA, Duration.ofNanos(periodA)),
                RateLimitBudget.tokenBucket(
                    "b", ScopeKind.EGRESS, capB, refillB, Duration.ofNanos(periodB))),
            new RateLimitOperation("ab", RateLimitPriority.EXECUTION, Map.of("a", 2L, "b", 1L), true),
            new RateLimitOperation("a3", RateLimitPriority.EXECUTION, Map.of("a", 3L), true),
            new RateLimitOperation("b2", RateLimitPriority.EXECUTION, Map.of("b", 2L), true),
            new RateLimitOperation("a1b2", RateLimitPriority.EXECUTION, Map.of("a", 1L, "b", 2L), true))
            .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofHours(1));
    String[] ops = {"ab", "a3", "b2", "a1b2", "ab", "a3", "b2", "a1b2", "ab", "ab"};
    long[][] costs = new long[ops.length][2];
    List<long[]> dispatched = new ArrayList<>();
    for (int i = 0; i < ops.length; i++) {
      String op = ops[i];
      costs[i][0] = op.equals("ab") ? 2 : op.equals("a3") ? 3 : op.equals("a1b2") ? 1 : 0;
      costs[i][1] = op.equals("ab") ? 1 : op.equals("b2") || op.equals("a1b2") ? 2 : 0;
      final long[] cost = costs[i];
      fixture.start(
          () ->
              fixture.context.execute(
                  policy,
                  RateLimitFixture.request(op),
                  SCOPE,
                  observer -> {
                    synchronized (dispatched) {
                      dispatched.add(new long[] {fixture.now(), cost[0], cost[1]});
                    }
                    return "ok";
                  }));
      fixture.awaitQuiescent();
    }
    for (int step = 0; step < 2000 && dispatched.size() < ops.length; step++) {
      fixture.advanceNanos(SEC / 10);
      fixture.awaitQuiescent();
    }
    assertThat(dispatched).hasSize(ops.length);
    for (long[] event : dispatched) {
      long usedA = 0;
      long usedB = 0;
      for (long[] other : dispatched) {
        if (other[0] <= event[0]) {
          usedA += other[1];
          usedB += other[2];
        }
      }
      // Everything dispatched up to this instant must be affordable by the cumulative supply.
      assertThat(usedA * periodA).isLessThanOrEqualTo(capA * periodA + refillA * event[0]);
      assertThat(usedB * periodB).isLessThanOrEqualTo(capB * periodB + refillB * event[0]);
    }
  }

  // ---- independent oracles ----------------------------------------------------------------

  /**
   * Token bucket on exact integers: level is scaled by the period so refill is {@code refill} per
   * nanosecond. Sequential single submitter.
   */
  private static long[] bucketOracle(
      long capacity, long refill, long period, long[] submit, long[] cost) {
    long[] out = new long[submit.length];
    long level = capacity * period;
    long last = 0;
    long prev = 0;
    for (int i = 0; i < submit.length; i++) {
      long t = Math.max(prev, submit[i]);
      level = Math.min(capacity * period, level + refill * (t - last));
      last = t;
      long need = cost[i] * period;
      if (level < need) {
        long wait = Math.ceilDiv(need - level, refill);
        t += wait;
        level = Math.min(capacity * period, level + refill * wait);
        last = t;
      }
      level -= need;
      out[i] = t;
      prev = t;
    }
    return out;
  }

  /** Two independent buckets; one request may need either or both. */
  private static long[] twoBucketOracle(
      long[] capacity, long[] refill, long[] period, long[][] cost) {
    long[] out = new long[cost.length];
    long[] level = {capacity[0] * period[0], capacity[1] * period[1]};
    long last = 0;
    long t = 0;
    for (int i = 0; i < cost.length; i++) {
      long when = t;
      for (int b = 0; b < 2; b++) {
        long atWhen =
            Math.min(capacity[b] * period[b], level[b] + refill[b] * (when - last));
        long need = cost[i][b] * period[b];
        if (cost[i][b] > 0 && atWhen < need) {
          // time to accumulate the shortfall from the state at `last`
          long wait = Math.ceilDiv(need - level[b], refill[b]);
          t = Math.max(t, last + wait);
        }
      }
      for (int b = 0; b < 2; b++) {
        level[b] = Math.min(capacity[b] * period[b], level[b] + refill[b] * (t - last));
        level[b] -= cost[i][b] * period[b];
      }
      last = t;
      out[i] = t;
    }
    return out;
  }

  /** Fixed windows aligned to multiples of {@code window} since the wall origin (time zero). */
  private static long[] fixedOracle(long limit, long window, long[] submit, long[] cost) {
    long[] out = new long[submit.length];
    Map<Long, Long> used = new java.util.HashMap<>();
    long prev = 0;
    for (int i = 0; i < submit.length; i++) {
      long t = Math.max(prev, submit[i]);
      while (true) {
        long index = Math.floorDiv(t, window);
        if (used.getOrDefault(index, 0L) + cost[i] <= limit) {
          used.merge(index, cost[i], Long::sum);
          break;
        }
        t = (index + 1) * window;
      }
      out[i] = t;
      prev = t;
    }
    return out;
  }

  /** A unit leaves the rolling window exactly {@code window} after its consumption. */
  private static long[] rollingOracle(long limit, long window, long[] submit, long[] cost) {
    long[] out = new long[submit.length];
    long prev = 0;
    for (int i = 0; i < submit.length; i++) {
      long t = Math.max(prev, submit[i]);
      while (true) {
        long inWindow = 0;
        long nextExpiry = Long.MAX_VALUE;
        for (int j = 0; j < i; j++) {
          if (out[j] + window > t) {
            inWindow += cost[j];
            nextExpiry = Math.min(nextExpiry, out[j] + window);
          }
        }
        if (inWindow + cost[i] <= limit) {
          break;
        }
        t = nextExpiry;
      }
      out[i] = t;
      prev = t;
    }
    return out;
  }
}
