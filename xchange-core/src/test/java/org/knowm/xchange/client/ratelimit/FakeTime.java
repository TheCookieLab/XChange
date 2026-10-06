package org.knowm.xchange.client.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic monotonic and wall clock driven only by explicit advances. */
final class FakeTime {

  static final long START_NANOS = 7_000_000_000_000L;
  /** Aligned to a second so fixed windows of whole seconds start exactly at test time zero. */
  static final Instant WALL_ORIGIN = Instant.parse("2026-01-01T00:00:00Z");

  private final AtomicLong nanos = new AtomicLong(START_NANOS);

  long nanoTime() {
    return nanos.get();
  }

  long elapsedNanos() {
    return nanos.get() - START_NANOS;
  }

  void advanceBy(long delta) {
    if (delta < 0) {
      throw new IllegalArgumentException("time never goes backwards");
    }
    nanos.addAndGet(delta);
  }

  void advanceTo(long target) {
    nanos.accumulateAndGet(target, Math::max);
  }

  Clock wallClock() {
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return WALL_ORIGIN.plus(Duration.ofNanos(elapsedNanos()));
      }
    };
  }
}
