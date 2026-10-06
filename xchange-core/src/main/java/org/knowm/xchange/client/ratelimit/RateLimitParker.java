package org.knowm.xchange.client.ratelimit;

import java.util.concurrent.locks.LockSupport;

/**
 * Waiting abstraction of the scheduler so tests can drive a fake monotonic clock. Implementations
 * follow {@link LockSupport} permit semantics: an {@link #unpark} before or during {@link
 * #parkUntil} makes the park return; spurious returns are allowed because the scheduler always
 * re-checks under its lock.
 */
interface RateLimitParker {

  /**
   * Parks the calling thread until the monotonic clock reaches {@code deadlineNanos}, the thread is
   * unparked, or the thread is interrupted. Must not clear the interrupt flag.
   *
   * @param deadlineNanos absolute time in the scheduler's monotonic nanosecond domain
   */
  void parkUntil(long deadlineNanos);

  /**
   * Makes the thread's current or next {@link #parkUntil} return.
   *
   * @param thread the waiter's thread
   */
  void unpark(Thread thread);

  /** The production parker over {@link System#nanoTime()}. */
  static RateLimitParker system() {
    return new RateLimitParker() {
      @Override
      public void parkUntil(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining > 0) {
          LockSupport.parkNanos(remaining);
        }
      }

      @Override
      public void unpark(Thread thread) {
        LockSupport.unpark(thread);
      }
    };
  }
}
