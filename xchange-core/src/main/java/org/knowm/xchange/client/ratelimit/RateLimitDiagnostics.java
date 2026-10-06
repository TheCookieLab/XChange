package org.knowm.xchange.client.ratelimit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Reason;

/**
 * Immutable aggregate snapshot of a {@link RateLimitContext}. It deliberately contains no scope
 * identities, credentials or budget keys: scopes are opaque application bindings and only their
 * number is reported.
 *
 * @since 1.0.3
 */
public final class RateLimitDiagnostics {

  private final long totalWaitNanos;
  private final long admissions;
  private final long retries;
  private final long ratePressureEvents;
  private final Map<Reason, Long> terminations;
  private final Map<String, String> policyVersions;
  private final Set<String> disabledNamespaces;
  private final Map<RateLimitPriority, Integer> pending;
  private final int trackedScopeCount;
  private final boolean closed;

  RateLimitDiagnostics(
      RateLimitScheduler.Snapshot snapshot,
      Map<String, String> policyVersions,
      Set<String> disabledNamespaces) {
    this.totalWaitNanos = snapshot.totalWaitNanos;
    this.admissions = snapshot.admissions;
    this.retries = snapshot.retries;
    this.ratePressureEvents = snapshot.ratePressureEvents;
    this.terminations = Collections.unmodifiableMap(new EnumMap<>(snapshot.terminations));
    this.policyVersions = Collections.unmodifiableMap(new LinkedHashMap<>(policyVersions));
    this.disabledNamespaces = Collections.unmodifiableSet(new LinkedHashSet<>(disabledNamespaces));
    this.pending = Collections.unmodifiableMap(new EnumMap<>(snapshot.pending));
    this.trackedScopeCount = snapshot.trackedScopes;
    this.closed = snapshot.closed;
  }

  /**
   * @return total time operations spent waiting for admission, in nanoseconds
   */
  public long getTotalWaitNanos() {
    return totalWaitNanos;
  }

  /**
   * @return number of admissions granted, one per wire attempt
   */
  public long getAdmissions() {
    return admissions;
  }

  /**
   * @return number of internal replays after a confirmed rate rejection
   */
  public long getRetries() {
    return retries;
  }

  /**
   * @return number of rate-rejection or ban feedbacks observed
   */
  public long getRatePressureEvents() {
    return ratePressureEvents;
  }

  /**
   * @return terminal outcomes by reason; every reason is present
   */
  public Map<Reason, Long> getTerminations() {
    return terminations;
  }

  /**
   * @return registered policy versions by namespace
   */
  public Map<String, String> getPolicyVersions() {
    return policyVersions;
  }

  /**
   * @return namespaces whose policy was explicitly disabled by the specification
   */
  public Set<String> getDisabledNamespaces() {
    return disabledNamespaces;
  }

  /**
   * @return operations currently waiting for admission per priority class
   */
  public Map<RateLimitPriority, Integer> getPending() {
    return pending;
  }

  /**
   * @return number of (budget, scope) states tracked; the scopes themselves are not exposed
   */
  public int getTrackedScopeCount() {
    return trackedScopeCount;
  }

  /**
   * @return whether the context was closed
   */
  public boolean isClosed() {
    return closed;
  }

  @Override
  public String toString() {
    return "RateLimitDiagnostics{totalWaitNanos="
        + totalWaitNanos
        + ", admissions="
        + admissions
        + ", retries="
        + retries
        + ", ratePressureEvents="
        + ratePressureEvents
        + ", terminations="
        + terminations
        + ", policyVersions="
        + policyVersions
        + ", disabledNamespaces="
        + disabledNamespaces
        + ", pending="
        + pending
        + ", trackedScopeCount="
        + trackedScopeCount
        + ", closed="
        + closed
        + '}';
  }
}
