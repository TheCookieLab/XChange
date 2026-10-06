package org.knowm.xchange.client.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable rate-limit policy of one exchange API family: namespace, version and dated source
 * references, budgets, request classifier, HTTP feedback interpretation, and the finite bounds for
 * waiting, replay and queueing.
 *
 * <p>All bounds are finite. Library defaults (used unless overridden through the {@code with*}
 * copy methods): maximum wait {@code EXECUTION} 5 s and {@code MARKET_DATA} 60 s for the whole
 * operation including replays, 3 attempts, pending limits 64 ({@code EXECUTION}) and 256 ({@code
 * MARKET_DATA}), fallback backoff 1 s base, 30 s cap, jitter fraction 0.25. Exchange modules are
 * expected to set explicit values with their rationale.
 *
 * <p>Policies are compared by identity; sharing a {@link RateLimitContext} compares namespace,
 * version and budget definitions (see {@link RateLimitContext#register}).
 *
 * @since 1.0.3
 */
public final class RateLimitPolicy {

  private static final Duration DEFAULT_MAX_WAIT_EXECUTION = Duration.ofSeconds(5);
  private static final Duration DEFAULT_MAX_WAIT_MARKET_DATA = Duration.ofSeconds(60);
  private static final int DEFAULT_MAX_ATTEMPTS = 3;
  private static final int DEFAULT_PENDING_EXECUTION = 64;
  private static final int DEFAULT_PENDING_MARKET_DATA = 256;
  private static final Duration DEFAULT_BACKOFF_BASE = Duration.ofSeconds(1);
  private static final Duration DEFAULT_BACKOFF_CAP = Duration.ofSeconds(30);
  private static final double DEFAULT_JITTER = 0.25;

  private final String namespace;
  private final String version;
  private final String source;
  private final Map<String, RateLimitBudget> budgets;
  private final Function<RateLimitRequest, RateLimitOperation> classifier;
  private final RateLimitFeedbackInterpreter feedbackInterpreter;
  private final Map<RateLimitPriority, Duration> maxWait;
  private final int maxAttempts;
  private final Map<RateLimitPriority, Integer> pendingLimit;
  private final Duration backoffBase;
  private final Duration backoffCap;
  private final double jitterFraction;

  /**
   * Creates a policy with the library default bounds.
   *
   * @param namespace provider/API namespace, for example {@code "coinbase.brokerage"}
   * @param version policy version recorded in diagnostics
   * @param source dated documentation references the policy is derived from
   * @param budgets the budgets of the policy (unique ids)
   * @param classifier maps a logical request to its operation; returning {@code null} rejects the
   *     request as unclassified before anything is sent
   * @param feedbackInterpreter declares the meaning of HTTP statuses and headers
   * @throws IllegalArgumentException if a value is blank, duplicated or out of range
   */
  public RateLimitPolicy(
      String namespace,
      String version,
      String source,
      List<RateLimitBudget> budgets,
      Function<RateLimitRequest, RateLimitOperation> classifier,
      RateLimitFeedbackInterpreter feedbackInterpreter) {
    this(
        requireText(namespace, "namespace"),
        requireText(version, "version"),
        Objects.requireNonNull(source, "source"),
        indexBudgets(budgets),
        Objects.requireNonNull(classifier, "classifier"),
        Objects.requireNonNull(feedbackInterpreter, "feedbackInterpreter"),
        defaultMaxWait(),
        DEFAULT_MAX_ATTEMPTS,
        defaultPending(),
        DEFAULT_BACKOFF_BASE,
        DEFAULT_BACKOFF_CAP,
        DEFAULT_JITTER);
  }

  private RateLimitPolicy(
      String namespace,
      String version,
      String source,
      Map<String, RateLimitBudget> budgets,
      Function<RateLimitRequest, RateLimitOperation> classifier,
      RateLimitFeedbackInterpreter feedbackInterpreter,
      Map<RateLimitPriority, Duration> maxWait,
      int maxAttempts,
      Map<RateLimitPriority, Integer> pendingLimit,
      Duration backoffBase,
      Duration backoffCap,
      double jitterFraction) {
    this.namespace = namespace;
    this.version = version;
    this.source = source;
    this.budgets = budgets;
    this.classifier = classifier;
    this.feedbackInterpreter = feedbackInterpreter;
    this.maxWait = maxWait;
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1");
    }
    this.maxAttempts = maxAttempts;
    this.pendingLimit = pendingLimit;
    validateBackoff(backoffBase, backoffCap, jitterFraction);
    this.backoffBase = backoffBase;
    this.backoffCap = backoffCap;
    this.jitterFraction = jitterFraction;
  }

  private static String requireText(String value, String what) {
    if (Objects.requireNonNull(value, what).trim().isEmpty()) {
      throw new IllegalArgumentException(what + " must not be blank");
    }
    return value;
  }

  private static Map<String, RateLimitBudget> indexBudgets(List<RateLimitBudget> budgets) {
    Map<String, RateLimitBudget> index = new LinkedHashMap<>();
    for (RateLimitBudget budget : Objects.requireNonNull(budgets, "budgets")) {
      Objects.requireNonNull(budget, "budget");
      if (index.put(budget.getId(), budget) != null) {
        throw new IllegalArgumentException("duplicate budget id " + budget.getId());
      }
    }
    return Collections.unmodifiableMap(index);
  }

  private static Map<RateLimitPriority, Duration> defaultMaxWait() {
    Map<RateLimitPriority, Duration> map = new EnumMap<>(RateLimitPriority.class);
    map.put(RateLimitPriority.EXECUTION, DEFAULT_MAX_WAIT_EXECUTION);
    map.put(RateLimitPriority.MARKET_DATA, DEFAULT_MAX_WAIT_MARKET_DATA);
    return Collections.unmodifiableMap(map);
  }

  private static Map<RateLimitPriority, Integer> defaultPending() {
    Map<RateLimitPriority, Integer> map = new EnumMap<>(RateLimitPriority.class);
    map.put(RateLimitPriority.EXECUTION, DEFAULT_PENDING_EXECUTION);
    map.put(RateLimitPriority.MARKET_DATA, DEFAULT_PENDING_MARKET_DATA);
    return Collections.unmodifiableMap(map);
  }

  private static void validateBackoff(Duration base, Duration cap, double jitter) {
    Objects.requireNonNull(base, "backoff base");
    Objects.requireNonNull(cap, "backoff cap");
    if (base.isZero() || base.isNegative()) {
      throw new IllegalArgumentException("backoff base must be positive");
    }
    if (cap.compareTo(base) < 0) {
      throw new IllegalArgumentException("backoff cap must be >= base");
    }
    if (!(jitter >= 0.0 && jitter < 1.0)) {
      throw new IllegalArgumentException("jitter fraction must be in [0, 1)");
    }
  }

  private RateLimitPolicy copy(
      Map<String, RateLimitBudget> newBudgets,
      Map<RateLimitPriority, Duration> newMaxWait,
      int newMaxAttempts,
      Map<RateLimitPriority, Integer> newPending,
      Duration newBase,
      Duration newCap,
      double newJitter) {
    return new RateLimitPolicy(
        namespace,
        version,
        source,
        newBudgets,
        classifier,
        feedbackInterpreter,
        newMaxWait,
        newMaxAttempts,
        newPending,
        newBase,
        newCap,
        newJitter);
  }

  /**
   * Copy with one budget replaced (same id) or added. The context rejects the copy if another
   * policy already registered a different definition of that id.
   *
   * @param budget the replacement or additional budget
   * @return the copy
   */
  public RateLimitPolicy withBudget(RateLimitBudget budget) {
    Objects.requireNonNull(budget, "budget");
    Map<String, RateLimitBudget> map = new LinkedHashMap<>(budgets);
    map.put(budget.getId(), budget);
    return copy(
        Collections.unmodifiableMap(map),
        maxWait,
        maxAttempts,
        pendingLimit,
        backoffBase,
        backoffCap,
        jitterFraction);
  }

  /**
   * Copy with a different maximum total wait for one priority class.
   *
   * @param priority the class
   * @param limit finite positive maximum wait for the whole operation including replays
   * @return the copy
   */
  public RateLimitPolicy withMaxWait(RateLimitPriority priority, Duration limit) {
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(limit, "limit");
    if (limit.isZero() || limit.isNegative()) {
      throw new IllegalArgumentException("max wait must be positive");
    }
    Map<RateLimitPriority, Duration> map = new EnumMap<>(RateLimitPriority.class);
    map.putAll(maxWait);
    map.put(priority, limit);
    return copy(
        budgets,
        Collections.unmodifiableMap(map),
        maxAttempts,
        pendingLimit,
        backoffBase,
        backoffCap,
        jitterFraction);
  }

  /**
   * Copy with a different maximum number of wire attempts per operation (first attempt included).
   *
   * @param attempts at least 1
   * @return the copy
   */
  public RateLimitPolicy withMaxAttempts(int attempts) {
    return copy(budgets, maxWait, attempts, pendingLimit, backoffBase, backoffCap, jitterFraction);
  }

  /**
   * Copy with a different pending-queue bound for one priority class.
   *
   * @param priority the class
   * @param limit at least 1
   * @return the copy
   */
  public RateLimitPolicy withPendingLimit(RateLimitPriority priority, int limit) {
    Objects.requireNonNull(priority, "priority");
    if (limit < 1) {
      throw new IllegalArgumentException("pending limit must be >= 1");
    }
    Map<RateLimitPriority, Integer> map = new EnumMap<>(RateLimitPriority.class);
    map.putAll(pendingLimit);
    map.put(priority, limit);
    return copy(
        budgets,
        maxWait,
        maxAttempts,
        Collections.unmodifiableMap(map),
        backoffBase,
        backoffCap,
        jitterFraction);
  }

  /**
   * Copy with a different fallback backoff used when a rate rejection carries no usable reset.
   * The delay of the n-th rejection of one operation is {@code min(cap, base * 2^(n-1))}, reduced
   * by a random fraction of at most {@code jitterFraction}, so it is always positive and never
   * above the cap.
   *
   * @param base positive base delay
   * @param cap cap, at least the base
   * @param jitterFraction in [0, 1)
   * @return the copy
   */
  public RateLimitPolicy withFallbackBackoff(Duration base, Duration cap, double jitterFraction) {
    return copy(budgets, maxWait, maxAttempts, pendingLimit, base, cap, jitterFraction);
  }

  /**
   * @return provider/API namespace
   */
  public String getNamespace() {
    return namespace;
  }

  /**
   * @return policy version
   */
  public String getVersion() {
    return version;
  }

  /**
   * @return dated documentation references
   */
  public String getSource() {
    return source;
  }

  /**
   * @return unmodifiable list of the budgets, in declaration order
   */
  public List<RateLimitBudget> getBudgets() {
    return Collections.unmodifiableList(new ArrayList<>(budgets.values()));
  }

  /**
   * @param budgetId budget identity
   * @return the budget, or {@code null} if the policy does not declare it
   */
  public RateLimitBudget getBudget(String budgetId) {
    return budgets.get(budgetId);
  }

  /**
   * @return the HTTP feedback interpreter
   */
  public RateLimitFeedbackInterpreter getFeedbackInterpreter() {
    return feedbackInterpreter;
  }

  /**
   * Classifies a logical request.
   *
   * @param request the request
   * @return the operation, or {@code null} if the policy does not know the request
   */
  public RateLimitOperation classify(RateLimitRequest request) {
    return classifier.apply(Objects.requireNonNull(request, "request"));
  }

  /**
   * @param priority the class
   * @return maximum total wait for an operation of that class, replays included
   */
  public Duration maxWait(RateLimitPriority priority) {
    return maxWait.get(priority);
  }

  /**
   * @return maximum wire attempts per operation, first attempt included
   */
  public int getMaxAttempts() {
    return maxAttempts;
  }

  /**
   * @param priority the class
   * @return the pending-queue bound of that class
   */
  public int pendingLimit(RateLimitPriority priority) {
    return pendingLimit.get(priority);
  }

  /**
   * @return fallback backoff base delay
   */
  public Duration getFallbackBackoffBase() {
    return backoffBase;
  }

  /**
   * @return fallback backoff cap
   */
  public Duration getFallbackBackoffCap() {
    return backoffCap;
  }

  /**
   * @return fallback backoff jitter fraction in [0, 1)
   */
  public double getFallbackBackoffJitter() {
    return jitterFraction;
  }

  /** Fallback delay in nanoseconds for the n-th (1-based) rejection; unit is a uniform in [0,1). */
  long fallbackBackoffNanos(int rejection, double unit) {
    long cap = saturatedNanos(backoffCap);
    long delay = saturatedNanos(backoffBase);
    for (int i = 1; i < rejection && delay < cap; i++) {
      delay = delay > cap / 2 ? cap : delay * 2;
    }
    delay = Math.min(delay, cap);
    long reduction = (long) (delay * jitterFraction * unit);
    return Math.max(1L, delay - Math.max(0L, reduction));
  }

  static long saturatedNanos(Duration duration) {
    try {
      return duration.toNanos();
    } catch (ArithmeticException e) {
      return RateLimitScheduler.MAX_DELAY_NANOS;
    }
  }

  @Override
  public String toString() {
    return "RateLimitPolicy{namespace="
        + namespace
        + ", version="
        + version
        + ", budgets="
        + budgets.keySet()
        + '}';
  }
}
