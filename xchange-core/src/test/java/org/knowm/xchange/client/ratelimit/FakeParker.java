package org.knowm.xchange.client.ratelimit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic parking. In auto-advance mode a park jumps the fake clock to the wake-up target
 * (single-threaded tests). In blocking mode a parked thread really blocks until it is unparked,
 * interrupted, or the fake clock reaches its target, so a test thread controls time explicitly and
 * can wait until a known number of threads is parked and idle.
 */
final class FakeParker implements RateLimitParker {

  private static final long AWAIT_LIMIT_NANOS = TimeUnit.SECONDS.toNanos(20);

  private final FakeTime time;
  private final boolean autoAdvance;
  private final Object lock = new Object();
  private final Set<Thread> permits = new HashSet<>();
  private final Map<Thread, Long> parked = new HashMap<>();

  FakeParker(FakeTime time, boolean autoAdvance) {
    this.time = time;
    this.autoAdvance = autoAdvance;
  }

  @Override
  public void parkUntil(long target) {
    Thread self = Thread.currentThread();
    if (autoAdvance) {
      if (!self.isInterrupted()) {
        time.advanceTo(target);
      }
      return;
    }
    synchronized (lock) {
      if (permits.remove(self) || self.isInterrupted() || reached(target)) {
        return;
      }
      parked.put(self, target);
      lock.notifyAll();
      try {
        while (true) {
          try {
            lock.wait();
          } catch (InterruptedException e) {
            self.interrupt();
            return;
          }
          if (permits.remove(self) || reached(target)) {
            return;
          }
        }
      } finally {
        parked.remove(self);
        lock.notifyAll();
      }
    }
  }

  @Override
  public void unpark(Thread thread) {
    synchronized (lock) {
      permits.add(thread);
      lock.notifyAll();
    }
  }

  /** Wakes every parked thread to re-check the fake clock; also signals awaiting test threads. */
  void poke() {
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  /** Blocks until at least {@code count} threads are parked with no pending wake-up. */
  void awaitIdle(int count) {
    await(() -> idle() >= count, "expected " + count + " idle parked threads, saw " + idleSnapshot());
  }

  /** Blocks until every one of {@code total} workers is either finished or idle-parked. */
  void awaitQuiescent(int total, AtomicInteger finished) {
    await(
        () -> idle() + finished.get() >= total,
        "workers never quiesced: total=" + total + " finished=" + finished.get());
  }

  private int idleSnapshot() {
    synchronized (lock) {
      return idle();
    }
  }

  private int idle() {
    int count = 0;
    for (Map.Entry<Thread, Long> entry : parked.entrySet()) {
      // A thread whose permit or wake-up time already arrived is about to run: not idle.
      if (!permits.contains(entry.getKey()) && !reached(entry.getValue())) {
        count++;
      }
    }
    return count;
  }

  private boolean reached(long target) {
    return time.nanoTime() - target >= 0;
  }

  private void await(java.util.function.BooleanSupplier condition, String failure) {
    long limit = System.nanoTime() + AWAIT_LIMIT_NANOS;
    synchronized (lock) {
      while (!condition.getAsBoolean()) {
        long remaining = limit - System.nanoTime();
        if (remaining <= 0) {
          throw new AssertionError(failure);
        }
        try {
          lock.wait(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError("interrupted while waiting: " + failure, e);
        }
      }
    }
  }
}
