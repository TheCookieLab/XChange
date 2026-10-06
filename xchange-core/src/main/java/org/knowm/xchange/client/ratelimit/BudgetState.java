package org.knowm.xchange.client.ratelimit;

import java.util.ArrayDeque;

/**
 * Mutable state of one budget in one scope. Not thread-safe: every method is called with the
 * scheduler lock held. All times are in the scheduler's monotonic nanosecond domain and are
 * compared by signed difference so counter wrap-around is harmless. Availability is never rounded
 * upward into an early permit: waits are ceiling divisions of exact integer quantities.
 */
abstract class BudgetState {

  private boolean cooled;
  private long cooldownUntil;

  /** Epoch stamp of the drain pass in which an earlier waiter claimed this state. */
  long claimedEpoch;

  /** Whether {@code a} is strictly later than {@code b}. */
  static boolean after(long a, long b) {
    return a - b > 0;
  }

  static BudgetState create(RateLimitBudget budget, long nanoOrigin, long epochNanosOrigin) {
    switch (budget.getLaw()) {
      case TOKEN_BUCKET:
        return new TokenBucket(budget);
      case FIXED_WINDOW:
        return new FixedWindow(budget, nanoOrigin, epochNanosOrigin);
      case ROLLING_WINDOW:
        return new RollingWindow(budget);
      default:
        throw new IllegalStateException("unknown law " + budget.getLaw());
    }
  }

  /** Tighten-only: a later cooldown replaces an earlier one, never the reverse. */
  final void cooldown(long until) {
    if (!cooled || after(until, cooldownUntil)) {
      cooled = true;
      cooldownUntil = until;
    }
  }

  final boolean coolingDown(long now) {
    return cooled && after(cooldownUntil, now);
  }

  final long cooldownDeadline() {
    return cooldownUntil;
  }

  /**
   * Earliest instant at which {@code cost} can be paid, honouring the cooldown; {@code now} if it
   * can be paid immediately.
   */
  final long availableAt(long cost, long now) {
    long at = lawAvailableAt(cost, now);
    if (coolingDown(now) && after(cooldownUntil, at)) {
      return cooldownUntil;
    }
    return at;
  }

  /** Spends {@code cost}; the caller has established {@code availableAt(cost, now) == now}. */
  abstract void debit(long cost, long now);

  abstract long lawAvailableAt(long cost, long now);

  /** Continuous token bucket with exact scaled-integer token arithmetic. */
  private static final class TokenBucket extends BudgetState {
    private final long periodNanos;
    private final long refill;
    private final long capacityScaled;
    private long scaled;
    private long last;
    private boolean started;

    TokenBucket(RateLimitBudget budget) {
      this.periodNanos = budget.periodNanos();
      this.refill = budget.getRefillAmount();
      this.capacityScaled = budget.getCapacity() * periodNanos;
      this.scaled = capacityScaled;
    }

    private void refillTo(long now) {
      if (!started) {
        started = true;
        last = now;
        return;
      }
      long elapsed = now - last;
      if (elapsed <= 0) {
        return;
      }
      last = now;
      long missing = capacityScaled - scaled;
      if (missing == 0) {
        return;
      }
      long toFull = ceilDiv(missing, refill);
      scaled = elapsed >= toFull ? capacityScaled : scaled + refill * elapsed;
    }

    @Override
    long lawAvailableAt(long cost, long now) {
      refillTo(now);
      long need = cost * periodNanos;
      if (scaled >= need) {
        return now;
      }
      return now + Math.min(ceilDiv(need - scaled, refill), RateLimitScheduler.MAX_DELAY_NANOS);
    }

    @Override
    void debit(long cost, long now) {
      refillTo(now);
      long need = cost * periodNanos;
      if (scaled < need) {
        throw new IllegalStateException("debit without capacity");
      }
      scaled -= need;
    }
  }

  /** Fixed window aligned to the wall-clock epoch (window index = epoch nanos / window). */
  private static final class FixedWindow extends BudgetState {
    private final long limit;
    private final long windowNanos;
    private final long nanoOrigin;
    private final long epochOrigin;
    private long windowIndex = Long.MIN_VALUE;
    private long used;

    FixedWindow(RateLimitBudget budget, long nanoOrigin, long epochOrigin) {
      this.limit = budget.getCapacity();
      this.windowNanos = budget.periodNanos();
      this.nanoOrigin = nanoOrigin;
      this.epochOrigin = epochOrigin;
    }

    private long epochNanos(long now) {
      return epochOrigin + (now - nanoOrigin);
    }

    private long roll(long now) {
      long epoch = epochNanos(now);
      long index = Math.floorDiv(epoch, windowNanos);
      if (index > windowIndex) {
        windowIndex = index;
        used = 0;
      }
      return epoch;
    }

    @Override
    long lawAvailableAt(long cost, long now) {
      long epoch = roll(now);
      if (used + cost <= limit) {
        return now;
      }
      long nextStart = (windowIndex + 1) * windowNanos;
      return now + (nextStart - epoch);
    }

    @Override
    void debit(long cost, long now) {
      roll(now);
      if (used + cost > limit) {
        throw new IllegalStateException("debit without capacity");
      }
      used += cost;
    }
  }

  /** Rolling window: an event of cost c at time e is counted during [e, e + window). */
  private static final class RollingWindow extends BudgetState {
    private static final class Event {
      final long at;
      final long cost;

      Event(long at, long cost) {
        this.at = at;
        this.cost = cost;
      }
    }

    private final long limit;
    private final long windowNanos;
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long used;

    RollingWindow(RateLimitBudget budget) {
      this.limit = budget.getCapacity();
      this.windowNanos = budget.periodNanos();
    }

    private void expire(long now) {
      Event oldest = events.peekFirst();
      while (oldest != null && !after(oldest.at + windowNanos, now)) {
        used -= oldest.cost;
        events.pollFirst();
        oldest = events.peekFirst();
      }
    }

    @Override
    long lawAvailableAt(long cost, long now) {
      expire(now);
      long excess = used + cost - limit;
      if (excess <= 0) {
        return now;
      }
      long freed = 0;
      for (Event event : events) {
        freed += event.cost;
        if (freed >= excess) {
          return event.at + windowNanos;
        }
      }
      throw new IllegalStateException("cost exceeds window limit");
    }

    @Override
    void debit(long cost, long now) {
      expire(now);
      if (used + cost > limit) {
        throw new IllegalStateException("debit without capacity");
      }
      events.addLast(new Event(now, cost));
      used += cost;
    }
  }

  static long ceilDiv(long numerator, long denominator) {
    long quotient = numerator / denominator;
    return numerator % denominator == 0 ? quotient : quotient + 1;
  }
}
