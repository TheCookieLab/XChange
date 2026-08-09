package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v3.dto.RetryClassification;

/**
 * Bounded, jittered retry for Coinbase Advanced Trade read operations.
 *
 * <p>Retries are only attempted for failures classified {@link RetryClassification#TRANSIENT} or
 * {@link RetryClassification#RATE_CREDIT}, and only for replay-safe read calls. Placement-style
 * operations are never routed through this helper: their outcomes may be ambiguous and must be
 * reconciled, not replayed. The backoff mirrors the Coinbase Derivatives transport (jittered
 * linear delay, bounded attempts).
 */
public final class CoinbaseRetry {

  /** Maximum attempts per read call, mirroring the derivatives transport bound. */
  public static final int MAX_ATTEMPTS = 3;

  private static final long BACKOFF_MIN_MS = 25L;
  private static final long BACKOFF_MAX_MS = 76L;

  private CoinbaseRetry() {}

  /** A replay-safe (read-only) provider call. */
  @FunctionalInterface
  public interface ReadCall<T> {

    T call() throws IOException;
  }

  /**
   * Invokes the read call, retrying rate-credit and transient failures with jittered backoff up to
   * {@link #MAX_ATTEMPTS}. Deterministic failures (authentication, permanent, ambiguous) are
   * rethrown without retry.
   *
   * @param call the read operation
   * @return the first successful result
   * @throws CoinbaseException when the last attempt still fails with a retryable classification
   * @throws IOException for transport failures
   */
  public static <T> T readWithBackoff(ReadCall<T> call) throws IOException {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return call.call();
      } catch (CoinbaseException failure) {
        RetryClassification classification = failure.getRetryClassification();
        if (classification != RetryClassification.TRANSIENT
            && classification != RetryClassification.RATE_CREDIT) {
          throw failure;
        }
        if (attempt == MAX_ATTEMPTS) {
          throw failure;
        }
        sleep(attempt);
      }
    }
    throw new IllegalStateException("Unreachable Coinbase retry state");
  }

  private static void sleep(int attempt) throws IOException {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(BACKOFF_MIN_MS, BACKOFF_MAX_MS) * attempt);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted during Coinbase retry backoff", interrupted);
    }
  }
}
