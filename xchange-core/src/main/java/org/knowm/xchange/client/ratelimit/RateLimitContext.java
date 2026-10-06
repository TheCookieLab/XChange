package org.knowm.xchange.client.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Dispatch;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Reason;

/**
 * Shareable, thread-safe owner of rate-limit state. Exchanges (or any direct transport) that share
 * one context and one user scope consume one allocation per budget, so construction order,
 * repeated specification copies, product views or credential rotation never mint a fresh burst:
 * budget state lives here, keyed by budget identity and scope, and is created lazily on first use.
 *
 * <p>The only operation callers need is {@link #execute}: it classifies the logical request,
 * admits it against every budget it needs atomically, runs the caller's attempt <em>after</em>
 * admission (so dynamic credentials are minted late), and applies confirmed rate-pressure feedback
 * to every sharer. Waiting is bounded by the policy's per-class maximum wait and by an optional
 * {@link RateLimitDeadline}. All terminal outcomes are {@link RateLimitTerminatedException}s
 * (a {@link org.knowm.xchange.exceptions.RateLimitExceededException}).
 *
 * @since 1.0.3
 */
public final class RateLimitContext implements AutoCloseable {

  private static final String DEFAULT_USER_SCOPE = "default";
  private static final String EGRESS_SCOPE = "egress";

  private final RateLimitScheduler scheduler;
  private final Clock wall;
  private final DoubleSupplier jitter;

  private final Object registration = new Object();
  private final Map<String, String> namespaceVersions = new LinkedHashMap<>();
  private final Map<String, RateLimitBudget> budgetDefinitions = new LinkedHashMap<>();
  private final Set<String> disabledNamespaces = new LinkedHashSet<>();
  private final Set<RateLimitPolicy> registered = ConcurrentHashMap.newKeySet();

  /** Creates a context on the system monotonic and wall clocks. */
  public RateLimitContext() {
    this(
        System::nanoTime,
        Clock.systemUTC(),
        RateLimitParker.system(),
        () -> ThreadLocalRandom.current().nextDouble());
  }

  RateLimitContext(
      LongSupplier nanoTime, Clock wallClock, RateLimitParker parker, DoubleSupplier jitter) {
    this.wall = Objects.requireNonNull(wallClock, "wallClock");
    this.scheduler = new RateLimitScheduler(nanoTime, wallClock, parker);
    this.jitter = Objects.requireNonNull(jitter, "jitter");
  }

  /**
   * Validates and captures a policy. Registering an identical definition again is a no-op and
   * never resets any state.
   *
   * @param policy the policy
   * @throws IllegalArgumentException if the namespace is already registered with another version
   *     or a budget id is already registered with a different definition; nothing is registered in
   *     that case
   */
  public void register(RateLimitPolicy policy) {
    Objects.requireNonNull(policy, "policy");
    synchronized (registration) {
      String knownVersion = namespaceVersions.get(policy.getNamespace());
      if (knownVersion != null && !knownVersion.equals(policy.getVersion())) {
        throw new IllegalArgumentException(
            "namespace "
                + policy.getNamespace()
                + " is already registered with policy version "
                + knownVersion
                + ", not "
                + policy.getVersion());
      }
      for (RateLimitBudget budget : policy.getBudgets()) {
        RateLimitBudget known = budgetDefinitions.get(budget.getId());
        if (known != null && !known.equals(budget)) {
          throw new IllegalArgumentException(
              "conflicting definition of budget " + budget.getId() + ": " + known + " vs " + budget);
        }
      }
      namespaceVersions.put(policy.getNamespace(), policy.getVersion());
      for (RateLimitBudget budget : policy.getBudgets()) {
        budgetDefinitions.putIfAbsent(budget.getId(), budget);
      }
      registered.add(policy);
    }
  }

  /**
   * Records that the specification explicitly disabled rate limiting for a policy, so the choice
   * stays visible in {@link #diagnostics()}. Nothing is enforced for the policy.
   *
   * @param policy the disabled policy
   */
  public void registerDisabled(RateLimitPolicy policy) {
    Objects.requireNonNull(policy, "policy");
    synchronized (registration) {
      disabledNamespaces.add(policy.getNamespace());
    }
  }

  /**
   * Executes one logical operation under the policy.
   *
   * <p>Only confirmed rate rejections of replay-safe operations are replayed, each replay going
   * through admission again; every other exception thrown by the attempt propagates unchanged.
   *
   * @param policy the policy (registered on first use)
   * @param request the logical request; the policy classifies it
   * @param userScope opaque application binding of the user allocation; {@code null} selects the
   *     conservative shared binding {@code "default"}
   * @param attempt the wire attempt; invoked only after admission, once per admitted attempt
   * @param <T> the result type
   * @return the attempt's result
   * @throws RateLimitTerminatedException for every terminal rate-limit outcome
   * @throws Exception anything the attempt throws that is not a rate rejection
   */
  public <T> T execute(
      RateLimitPolicy policy,
      RateLimitRequest request,
      String userScope,
      RateLimitAttempt<T> attempt)
      throws Exception {
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(attempt, "attempt");
    if (!registered.contains(policy)) {
      register(policy);
    }
    RateLimitOperation operation = policy.classify(request);
    if (operation == null) {
      throw scheduler.terminated(
          Reason.UNCLASSIFIED_OPERATION,
          Dispatch.NOT_SENT,
          "policy " + policy.getNamespace() + " does not classify " + request,
          null);
    }
    Plan plan = plan(policy, operation, userScope);
    long start = scheduler.now();
    long deadline = start + deadlineBudget(policy, operation.getPriority());
    if (!BudgetState.after(deadline, start)) {
      throw scheduler.terminated(
          Reason.DEADLINE_EXCEEDED, Dispatch.NOT_SENT, "operation deadline already expired", null);
    }
    int attempts = 0;
    int rejections = 0;
    while (true) {
      scheduler.admit(
          operation.getPriority(),
          plan.pendingKey,
          policy.pendingLimit(operation.getPriority()),
          plan.budgets,
          plan.keys,
          plan.costs,
          deadline);
      attempts++;
      checkBeforeDispatch(deadline);
      AttemptRecorder recorder = new AttemptRecorder(policy.getFeedbackInterpreter(), wall);
      T result = null;
      Exception failure = null;
      try {
        result = attempt.run(recorder);
      } catch (Exception e) {
        failure = e;
      }
      RateLimitFeedback feedback = recorder.feedback();
      if (feedback.getKind() == RateLimitFeedback.Kind.NONE) {
        if (failure != null) {
          throw failure;
        }
        return result;
      }
      scheduler.countRatePressure();
      rejections++;
      boolean banned = feedback.getKind() == RateLimitFeedback.Kind.BANNED;
      long delay = delayNanos(policy, feedback, banned, rejections);
      long observed = scheduler.now();
      long until = observed + delay;
      scheduler.cooldown(plan.budgets, plan.keys, until);
      boolean terminal =
          banned
              || recorder.uncertain()
              || !operation.isReplayOnRateLimit()
              || attempts >= policy.getMaxAttempts()
              || BudgetState.after(until, deadline);
      if (terminal) {
        throw scheduler.terminated(
            Reason.REMOTE_PRESSURE_EXHAUSTED,
            recorder.uncertain() ? Dispatch.UNCERTAIN : Dispatch.REJECTED,
            terminalMessage(operation, feedback, attempts, recorder.uncertain(), until, deadline),
            failure);
      }
      scheduler.countRetry();
    }
  }

  /**
   * @return an immutable aggregate snapshot; it exposes no scope identities
   */
  public RateLimitDiagnostics diagnostics() {
    RateLimitScheduler.Snapshot snapshot = scheduler.snapshot();
    synchronized (registration) {
      return new RateLimitDiagnostics(snapshot, namespaceVersions, disabledNamespaces);
    }
  }

  /** Terminates every waiter with {@code SHUTDOWN} and rejects all later operations. */
  @Override
  public void close() {
    scheduler.close();
  }

  @Override
  public String toString() {
    synchronized (registration) {
      return "RateLimitContext{policies=" + namespaceVersions + '}';
    }
  }

  private static final class Plan {
    final String pendingKey;
    final RateLimitBudget[] budgets;
    final String[] keys;
    final long[] costs;

    Plan(String pendingKey, RateLimitBudget[] budgets, String[] keys, long[] costs) {
      this.pendingKey = pendingKey;
      this.budgets = budgets;
      this.keys = keys;
      this.costs = costs;
    }
  }

  private Plan plan(RateLimitPolicy policy, RateLimitOperation operation, String userScope) {
    String user = userScope == null ? DEFAULT_USER_SCOPE : userScope;
    int size = operation.getRequirements().size();
    RateLimitBudget[] budgets = new RateLimitBudget[size];
    String[] keys = new String[size];
    long[] costs = new long[size];
    int index = 0;
    for (Map.Entry<String, Long> requirement : operation.getRequirements().entrySet()) {
      RateLimitBudget budget = policy.getBudget(requirement.getKey());
      if (budget == null) {
        throw scheduler.terminated(
            Reason.UNCLASSIFIED_OPERATION,
            Dispatch.NOT_SENT,
            "operation " + operation.getName() + " references undeclared budget " + requirement.getKey(),
            null);
      }
      long cost = requirement.getValue();
      if (cost > budget.getCapacity()) {
        throw scheduler.terminated(
            Reason.IMPOSSIBLE_COST,
            Dispatch.NOT_SENT,
            "operation "
                + operation.getName()
                + " costs "
                + cost
                + " but budget "
                + budget.getId()
                + " has capacity "
                + budget.getCapacity(),
            null);
      }
      String scope = budget.getScopeKind() == RateLimitBudget.ScopeKind.USER ? user : EGRESS_SCOPE;
      budgets[index] = budget;
      keys[index] = budget.getId() + '\u0000' + scope;
      costs[index] = cost;
      index++;
    }
    String pendingKey = policy.getNamespace() + '\u0000' + operation.getPriority();
    return new Plan(pendingKey, budgets, keys, costs);
  }

  /** Nanoseconds from now the operation may last: policy maximum, tightened by the thread scope. */
  private static long deadlineBudget(RateLimitPolicy policy, RateLimitPriority priority) {
    long budget = Math.min(RateLimitPolicy.saturatedNanos(policy.maxWait(priority)), RateLimitScheduler.MAX_DELAY_NANOS);
    Long remaining = RateLimitDeadline.remainingNanos();
    if (remaining != null && remaining < budget) {
      return remaining;
    }
    return budget;
  }

  private void checkBeforeDispatch(long deadline) {
    if (Thread.currentThread().isInterrupted()) {
      throw scheduler.terminated(
          Reason.CANCELLED, Dispatch.NOT_SENT, "interrupted after admission, before dispatch", null);
    }
    if (scheduler.isClosed()) {
      throw scheduler.terminated(
          Reason.SHUTDOWN, Dispatch.NOT_SENT, "rate limit context closed before dispatch", null);
    }
    if (BudgetState.after(scheduler.now(), deadline)) {
      throw scheduler.terminated(
          Reason.DEADLINE_EXCEEDED,
          Dispatch.NOT_SENT,
          "operation deadline expired after admission, before dispatch",
          null);
    }
  }

  private long delayNanos(
      RateLimitPolicy policy, RateLimitFeedback feedback, boolean banned, int rejections) {
    Duration reset = feedback.getRetryAfter();
    long nanos;
    if (reset != null) {
      nanos = RateLimitPolicy.saturatedNanos(reset);
    } else if (banned) {
      nanos = RateLimitPolicy.saturatedNanos(policy.getFallbackBackoffCap());
    } else {
      nanos = policy.fallbackBackoffNanos(rejections, jitter.getAsDouble());
    }
    return Math.min(nanos, RateLimitScheduler.MAX_DELAY_NANOS);
  }

  private static String terminalMessage(
      RateLimitOperation operation,
      RateLimitFeedback feedback,
      int attempts,
      boolean uncertain,
      long until,
      long deadline) {
    String why;
    if (uncertain) {
      why = "outcome uncertain, not replayed";
    } else if (feedback.getKind() == RateLimitFeedback.Kind.BANNED) {
      why = "banned by the exchange";
    } else if (!operation.isReplayOnRateLimit()) {
      why = "operation is not replay-safe";
    } else if (BudgetState.after(until, deadline)) {
      why = "cooldown ends after the operation deadline";
    } else {
      why = "attempt limit reached";
    }
    return "remote rate pressure on "
        + operation.getName()
        + " after "
        + attempts
        + " attempt(s): "
        + why;
  }
}
