package org.knowm.xchange.client.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Dispatch;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Reason;

/**
 * Atomic multi-budget admission with two priority classes.
 *
 * <p>One lock guards all state. Each waiter is a thread that parks <em>outside</em> the lock;
 * {@code drain} runs under the lock (it only does arithmetic, never I/O, sleeping, signing or user
 * callbacks) and grants or terminates waiters in precedence order: every {@code EXECUTION} waiter
 * in arrival order, then every {@code MARKET_DATA} waiter in arrival order. A waiter is granted
 * only if no earlier, not yet granted waiter has claimed one of its budget states and every
 * required budget can pay its full cost right now; the debit of all budgets then happens in the
 * same critical section, so a blocked request never partially consumes a budget. A waiter that is
 * in a cooldown on any of its states cannot use its place and therefore does not claim.
 *
 * <p>An earlier estimate of when a waiter can proceed is never a reservation: waiters re-run the
 * full check under the lock when they wake.
 */
final class RateLimitScheduler {

  /** Upper bound of any single delay; keeps signed nanosecond differences meaningful. */
  static final long MAX_DELAY_NANOS = Long.MAX_VALUE / 4;

  private enum Status {
    WAITING,
    GRANTED,
    TERMINATED
  }

  private static final class Waiter {
    final Thread thread;
    final RateLimitPriority priority;
    final String pendingKey;
    final BudgetState[] states;
    final long[] costs;
    final long deadline;
    final long enqueuedAt;
    Status status = Status.WAITING;
    Reason reason;
    String message;
    boolean hasWake;
    long wake;
    boolean armed;
    long armedUntil;

    Waiter(
        Thread thread,
        RateLimitPriority priority,
        String pendingKey,
        BudgetState[] states,
        long[] costs,
        long deadline,
        long enqueuedAt) {
      this.thread = thread;
      this.priority = priority;
      this.pendingKey = pendingKey;
      this.states = states;
      this.costs = costs;
      this.deadline = deadline;
      this.enqueuedAt = enqueuedAt;
    }

    long target() {
      return hasWake && BudgetState.after(deadline, wake) ? wake : deadline;
    }
  }

  private final LongSupplier nano;
  private final RateLimitParker parker;
  private final long nanoOrigin;
  private final long epochOrigin;

  private final ReentrantLock lock = new ReentrantLock();
  private final Map<String, BudgetState> states = new HashMap<>();
  private final List<Waiter> executionQueue = new ArrayList<>();
  private final List<Waiter> marketDataQueue = new ArrayList<>();
  private final Map<String, Integer> pending = new HashMap<>();
  private long drainEpoch;
  private boolean closed;

  private final AtomicLong admissions = new AtomicLong();
  private final AtomicLong totalWaitNanos = new AtomicLong();
  private final AtomicLong retries = new AtomicLong();
  private final AtomicLong ratePressureEvents = new AtomicLong();
  private final AtomicLongArray terminations = new AtomicLongArray(Reason.values().length);

  RateLimitScheduler(LongSupplier nano, Clock wall, RateLimitParker parker) {
    this.nano = nano;
    this.parker = parker;
    this.nanoOrigin = nano.getAsLong();
    this.epochOrigin = epochNanos(wall.instant());
  }

  private static long epochNanos(Instant instant) {
    try {
      return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    } catch (ArithmeticException e) {
      return 0L;
    }
  }

  long now() {
    return nano.getAsLong();
  }

  RateLimitTerminatedException terminated(
      Reason reason, Dispatch dispatch, String message, Throwable cause) {
    terminations.incrementAndGet(reason.ordinal());
    return new RateLimitTerminatedException(reason, dispatch, message, cause);
  }

  void countRetry() {
    retries.incrementAndGet();
  }

  void countRatePressure() {
    ratePressureEvents.incrementAndGet();
  }

  /**
   * Blocks until all {@code costs} were debited atomically from the {@code keys}, or throws a
   * terminal {@link RateLimitTerminatedException} with dispatch {@code NOT_SENT} (nothing debited
   * unless the exception reports a grant that was lost to the final check, which the caller owns).
   */
  void admit(
      RateLimitPriority priority,
      String pendingKey,
      int pendingLimit,
      RateLimitBudget[] budgets,
      String[] keys,
      long[] costs,
      long deadline) {
    Thread self = Thread.currentThread();
    if (self.isInterrupted()) {
      throw terminated(Reason.CANCELLED, Dispatch.NOT_SENT, "interrupted before admission", null);
    }
    lock.lock();
    Waiter waiter = null;
    try {
      if (closed) {
        throw terminated(Reason.SHUTDOWN, Dispatch.NOT_SENT, "rate limit context is closed", null);
      }
      long enqueueTime = nano.getAsLong();
      BudgetState[] resolved = new BudgetState[keys.length];
      for (int i = 0; i < keys.length; i++) {
        resolved[i] = stateFor(keys[i], budgets[i]);
      }
      waiter = new Waiter(self, priority, pendingKey, resolved, costs, deadline, enqueueTime);
      queueOf(priority).add(waiter);
      pending.merge(pendingKey, 1, Integer::sum);
      boolean first = true;
      while (true) {
        long now = nano.getAsLong();
        drain(now, self);
        if (waiter.status == Status.GRANTED) {
          return;
        }
        if (waiter.status == Status.TERMINATED) {
          throw terminated(waiter.reason, Dispatch.NOT_SENT, waiter.message, null);
        }
        if (first) {
          first = false;
          if (pending.get(pendingKey) > pendingLimit) {
            leave(waiter, Reason.QUEUE_SATURATED, "pending " + priority + " queue is full");
            drain(now, self);
            throw terminated(Reason.QUEUE_SATURATED, Dispatch.NOT_SENT, waiter.message, null);
          }
        }
        if (self.isInterrupted()) {
          leave(waiter, Reason.CANCELLED, "interrupted while waiting for admission");
          drain(now, self);
          throw terminated(Reason.CANCELLED, Dispatch.NOT_SENT, waiter.message, null);
        }
        long target = waiter.target();
        waiter.armed = true;
        waiter.armedUntil = target;
        lock.unlock();
        try {
          parker.parkUntil(target);
        } finally {
          lock.lock();
          waiter.armed = false;
        }
      }
    } finally {
      if (waiter != null && waiter.status == Status.WAITING) {
        leave(waiter, Reason.CANCELLED, "admission aborted");
        drain(nano.getAsLong(), self);
      }
      lock.unlock();
    }
  }

  /** Applies a tighten-only cooldown to the given states, then re-evaluates the waiters. */
  void cooldown(RateLimitBudget[] budgets, String[] keys, long until) {
    lock.lock();
    try {
      for (int i = 0; i < keys.length; i++) {
        stateFor(keys[i], budgets[i]).cooldown(until);
      }
      drain(nano.getAsLong(), null);
    } finally {
      lock.unlock();
    }
  }

  /** Terminates every waiter with SHUTDOWN and rejects all later admissions. */
  void close() {
    lock.lock();
    try {
      closed = true;
      terminateAll(executionQueue);
      terminateAll(marketDataQueue);
      pending.clear();
    } finally {
      lock.unlock();
    }
  }

  private void terminateAll(List<Waiter> queue) {
    for (Waiter waiter : queue) {
      waiter.status = Status.TERMINATED;
      waiter.reason = Reason.SHUTDOWN;
      waiter.message = "rate limit context was closed";
      parker.unpark(waiter.thread);
    }
    queue.clear();
  }

  boolean isClosed() {
    lock.lock();
    try {
      return closed;
    } finally {
      lock.unlock();
    }
  }

  /** Diagnostics inputs, read under the lock. */
  Snapshot snapshot() {
    lock.lock();
    try {
      Map<Reason, Long> byReason = new EnumMap<>(Reason.class);
      for (Reason reason : Reason.values()) {
        byReason.put(reason, terminations.get(reason.ordinal()));
      }
      Map<RateLimitPriority, Integer> queued = new EnumMap<>(RateLimitPriority.class);
      queued.put(RateLimitPriority.EXECUTION, executionQueue.size());
      queued.put(RateLimitPriority.MARKET_DATA, marketDataQueue.size());
      return new Snapshot(
          totalWaitNanos.get(),
          admissions.get(),
          retries.get(),
          ratePressureEvents.get(),
          byReason,
          queued,
          states.size(),
          closed);
    } finally {
      lock.unlock();
    }
  }

  /** Immutable copy of the scheduler counters. */
  static final class Snapshot {
    final long totalWaitNanos;
    final long admissions;
    final long retries;
    final long ratePressureEvents;
    final Map<Reason, Long> terminations;
    final Map<RateLimitPriority, Integer> pending;
    final int trackedScopes;
    final boolean closed;

    Snapshot(
        long totalWaitNanos,
        long admissions,
        long retries,
        long ratePressureEvents,
        Map<Reason, Long> terminations,
        Map<RateLimitPriority, Integer> pending,
        int trackedScopes,
        boolean closed) {
      this.totalWaitNanos = totalWaitNanos;
      this.admissions = admissions;
      this.retries = retries;
      this.ratePressureEvents = ratePressureEvents;
      this.terminations = terminations;
      this.pending = pending;
      this.trackedScopes = trackedScopes;
      this.closed = closed;
    }
  }

  private BudgetState stateFor(String key, RateLimitBudget budget) {
    BudgetState state = states.get(key);
    if (state == null) {
      state = BudgetState.create(budget, nanoOrigin, epochOrigin);
      states.put(key, state);
    }
    return state;
  }

  private List<Waiter> queueOf(RateLimitPriority priority) {
    return priority == RateLimitPriority.EXECUTION ? executionQueue : marketDataQueue;
  }

  /** Removes a waiting waiter from its queue and marks it terminated. Lock held. */
  private void leave(Waiter waiter, Reason reason, String message) {
    if (waiter.status != Status.WAITING) {
      return;
    }
    queueOf(waiter.priority).remove(waiter);
    decrementPending(waiter);
    waiter.status = Status.TERMINATED;
    waiter.reason = reason;
    waiter.message = message;
  }

  private void decrementPending(Waiter waiter) {
    pending.computeIfPresent(waiter.pendingKey, (k, count) -> count <= 1 ? null : count - 1);
  }

  /** Grants or terminates waiters in precedence order. Lock held; {@code self} may be null. */
  private void drain(long now, Thread self) {
    long epoch = ++drainEpoch;
    drainQueue(executionQueue, now, epoch, self);
    drainQueue(marketDataQueue, now, epoch, self);
  }

  private void drainQueue(List<Waiter> queue, long now, long epoch, Thread self) {
    int keep = 0;
    int size = queue.size();
    for (int i = 0; i < size; i++) {
      Waiter waiter = queue.get(i);
      if (evaluate(waiter, now, epoch, self)) {
        decrementPending(waiter);
      } else {
        queue.set(keep++, waiter);
      }
    }
    if (keep < size) {
      queue.subList(keep, size).clear();
    }
  }

  /** Returns true if the waiter left the queue (granted or terminated). */
  private boolean evaluate(Waiter waiter, long now, long epoch, Thread self) {
    boolean blocked = false;
    boolean cooling = false;
    long wake = now;
    for (int i = 0; i < waiter.states.length; i++) {
      BudgetState state = waiter.states[i];
      if (state.claimedEpoch == epoch) {
        blocked = true;
      }
      if (state.coolingDown(now)) {
        cooling = true;
      }
      long at = state.availableAt(waiter.costs[i], now);
      if (BudgetState.after(at, wake)) {
        wake = at;
      }
    }
    boolean payableNow = !BudgetState.after(wake, now);
    if (!blocked && payableNow) {
      for (int i = 0; i < waiter.states.length; i++) {
        waiter.states[i].debit(waiter.costs[i], now);
      }
      waiter.status = Status.GRANTED;
      admissions.incrementAndGet();
      totalWaitNanos.addAndGet(Math.max(0L, now - waiter.enqueuedAt));
      wakeThread(waiter, self);
      return true;
    }
    if (!BudgetState.after(waiter.deadline, now)) {
      terminateDeadline(waiter, "operation deadline expired while waiting for admission", self);
      return true;
    }
    if (!payableNow && BudgetState.after(wake, waiter.deadline)) {
      // Cooldowns and debits only ever push availability later, so this is final.
      terminateDeadline(waiter, "budgets cannot admit the operation before its deadline", self);
      return true;
    }
    if (!cooling) {
      for (BudgetState state : waiter.states) {
        state.claimedEpoch = epoch;
      }
    }
    waiter.hasWake = !payableNow;
    waiter.wake = wake;
    if (waiter.armed && waiter.thread != self) {
      long target = waiter.target();
      if (BudgetState.after(waiter.armedUntil, target)) {
        waiter.armedUntil = target;
        parker.unpark(waiter.thread);
      }
    }
    return false;
  }

  private void terminateDeadline(Waiter waiter, String message, Thread self) {
    waiter.status = Status.TERMINATED;
    waiter.reason = Reason.DEADLINE_EXCEEDED;
    waiter.message = message;
    wakeThread(waiter, self);
  }

  private void wakeThread(Waiter waiter, Thread self) {
    if (waiter.thread != self) {
      parker.unpark(waiter.thread);
    }
  }
}
