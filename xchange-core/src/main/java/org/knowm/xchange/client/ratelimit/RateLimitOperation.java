package org.knowm.xchange.client.ratelimit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable classification of one logical request: a name, a module-owned {@link
 * RateLimitPriority}, the budgets it consumes with their costs, and whether it may be replayed
 * after a confirmed rate rejection.
 *
 * @since 1.0.3
 */
public final class RateLimitOperation {

  private final String name;
  private final RateLimitPriority priority;
  private final Map<String, Long> requirements;
  private final boolean replayOnRateLimit;

  /**
   * Creates an operation.
   *
   * @param name credential-free operation name used in diagnostics and exception messages
   * @param priority service class
   * @param requirements budget id to cost (each cost &gt;= 1); may be empty for an unmetered
   *     operation
   * @param replayOnRateLimit {@code true} only for replay-safe reads or operations whose
   *     documented protocol makes replay after a confirmed rate rejection safe
   */
  public RateLimitOperation(
      String name,
      RateLimitPriority priority,
      Map<String, Long> requirements,
      boolean replayOnRateLimit) {
    this.name = Objects.requireNonNull(name, "name");
    this.priority = Objects.requireNonNull(priority, "priority");
    Map<String, Long> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Long> entry : Objects.requireNonNull(requirements, "requirements").entrySet()) {
      String budgetId = Objects.requireNonNull(entry.getKey(), "budget id");
      long cost = Objects.requireNonNull(entry.getValue(), "cost");
      if (cost < 1) {
        throw new IllegalArgumentException("operation " + name + ": cost for " + budgetId + " must be >= 1");
      }
      copy.put(budgetId, cost);
    }
    this.requirements = Collections.unmodifiableMap(copy);
    this.replayOnRateLimit = replayOnRateLimit;
  }

  /**
   * Convenience for an operation costing one unit of each listed budget.
   *
   * @param name credential-free operation name
   * @param priority service class
   * @param replayOnRateLimit whether replay after a confirmed rate rejection is safe
   * @param budgetIds budgets consumed, one unit each
   * @return the operation
   */
  public static RateLimitOperation unitCost(
      String name, RateLimitPriority priority, boolean replayOnRateLimit, String... budgetIds) {
    Map<String, Long> costs = new LinkedHashMap<>();
    for (String budgetId : budgetIds) {
      costs.put(budgetId, 1L);
    }
    return new RateLimitOperation(name, priority, costs, replayOnRateLimit);
  }

  /**
   * @return the operation name
   */
  public String getName() {
    return name;
  }

  /**
   * @return the service class
   */
  public RateLimitPriority getPriority() {
    return priority;
  }

  /**
   * @return unmodifiable budget id to cost map, in declaration order
   */
  public Map<String, Long> getRequirements() {
    return requirements;
  }

  /**
   * @return whether the operation may be replayed after a confirmed rate rejection
   */
  public boolean isReplayOnRateLimit() {
    return replayOnRateLimit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RateLimitOperation)) {
      return false;
    }
    RateLimitOperation other = (RateLimitOperation) o;
    return replayOnRateLimit == other.replayOnRateLimit
        && priority == other.priority
        && name.equals(other.name)
        && requirements.equals(other.requirements);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, priority, requirements, replayOnRateLimit);
  }

  @Override
  public String toString() {
    return "RateLimitOperation{name="
        + name
        + ", priority="
        + priority
        + ", requirements="
        + requirements
        + ", replayOnRateLimit="
        + replayOnRateLimit
        + '}';
  }
}
