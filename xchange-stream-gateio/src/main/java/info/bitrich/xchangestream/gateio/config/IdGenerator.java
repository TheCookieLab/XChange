package info.bitrich.xchangestream.gateio.config;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

public final class IdGenerator {

  @Getter private static final IdGenerator instance = new IdGenerator();
  private final AtomicLong lastRequestId = new AtomicLong();

  private IdGenerator() {}

  public Long requestIdRandom() {
    return ThreadLocalRandom.current().nextLong();
  }

  /** Returns a unique, increasing request ID on the Unix epoch microsecond scale. */
  public String requestIdTimeMicroseconds() {
    return Long.toString(
        lastRequestId.updateAndGet(
            previous -> Math.max(previous + 1, System.currentTimeMillis() * 1_000L)));
  }
}
