package org.knowm.xchange.client.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;

/** A context on fake clocks plus helpers shared by the rate-limit tests. */
final class RateLimitFixture {

  static final Duration SECOND = Duration.ofSeconds(1);
  static final long JOIN_LIMIT_SECONDS = 20;

  final FakeTime time = new FakeTime();
  final FakeParker parker;
  final RateLimitContext context;
  private final AtomicInteger finished = new AtomicInteger();
  private final List<Task<?>> tasks = new ArrayList<>();

  /** Creates a fixture on a fresh fake clock; {@code autoAdvance} selects the parking mode. */
  RateLimitFixture(boolean autoAdvance) {
    this.parker = new FakeParker(time, autoAdvance);
    this.context =
        new RateLimitContext(time::nanoTime, time.wallClock(), parker, () -> 0.5);
  }

  static RateLimitFixture autoAdvance() {
    return new RateLimitFixture(true);
  }

  static RateLimitFixture blocking() {
    return new RateLimitFixture(false);
  }

  /** A policy whose classifier looks operations up by request key. */
  static RateLimitPolicy policy(
      String namespace, List<RateLimitBudget> budgets, RateLimitOperation... operations) {
    Map<String, RateLimitOperation> byName = new HashMap<>();
    for (RateLimitOperation operation : operations) {
      byName.put(operation.getName(), operation);
    }
    return new RateLimitPolicy(
        namespace,
        "test-1",
        "unit test fixture",
        budgets,
        request -> byName.get(request.getOperationKey()),
        RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of(418)));
  }

  static RateLimitRequest request(String operation) {
    return new RateLimitRequest(operation, false);
  }

  /** Fake nanoseconds since the fixture's time zero. */
  long now() {
    return time.elapsedNanos();
  }

  /** Executes an operation that records the fake time of each dispatch and succeeds. */
  void run(RateLimitPolicy policy, String operation, String scope, List<Long> dispatches)
      throws Exception {
    context.execute(
        policy,
        request(operation),
        scope,
        observer -> {
          synchronized (dispatches) {
            dispatches.add(now());
          }
          return "ok";
        });
  }

  /** Advances the fake clock and wakes parked threads. */
  void advance(Duration delta) {
    advanceNanos(delta.toNanos());
  }

  void advanceNanos(long delta) {
    time.advanceBy(delta);
    parker.poke();
  }

  void awaitIdle(int count) {
    parker.awaitIdle(count);
  }

  void awaitQuiescent() {
    parker.awaitQuiescent(tasks.size(), finished);
  }

  /** Runs {@code body} on its own thread; the thread is tracked for quiescence. */
  <T> Task<T> start(Callable<T> body) {
    Task<T> task = new Task<>(body);
    tasks.add(task);
    task.thread.start();
    return task;
  }

  /** Interrupts leftovers so a failed test never leaves threads behind. */
  void cleanup() {
    context.close();
    for (Task<?> task : tasks) {
      task.thread.interrupt();
    }
    for (Task<?> task : tasks) {
      try {
        task.thread.join(TimeUnit.SECONDS.toMillis(JOIN_LIMIT_SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  /** One worker thread and its outcome. */
  final class Task<T> {
    final Thread thread;
    private T result;
    private Throwable failure;
    private boolean done;

    private Task(Callable<T> body) {
      this.thread =
          new Thread(
              () -> {
                try {
                  T value = body.call();
                  synchronized (this) {
                    result = value;
                  }
                } catch (Throwable t) {
                  synchronized (this) {
                    failure = t;
                  }
                } finally {
                  synchronized (this) {
                    done = true;
                  }
                  finished.incrementAndGet();
                  parker.poke();
                }
              },
              "ratelimit-test-worker");
      this.thread.setDaemon(true);
    }

    /** Waits for the task (real time bounded) and returns its failure, or {@code null}. */
    Throwable failure() {
      join();
      synchronized (this) {
        return failure;
      }
    }

    /** The terminal rate-limit exception of this task. */
    RateLimitTerminatedException terminated() {
      Throwable t = failure();
      if (!(t instanceof RateLimitTerminatedException)) {
        throw new AssertionError("expected RateLimitTerminatedException but was " + t, t);
      }
      return (RateLimitTerminatedException) t;
    }

    T result() {
      Throwable t = failure();
      if (t != null) {
        throw new AssertionError("task failed", t);
      }
      synchronized (this) {
        return result;
      }
    }

    boolean isDone() {
      synchronized (this) {
        return done;
      }
    }

    private void join() {
      try {
        thread.join(TimeUnit.SECONDS.toMillis(JOIN_LIMIT_SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted", e);
      }
      if (thread.isAlive()) {
        throw new AssertionError("worker did not finish");
      }
    }
  }
}
