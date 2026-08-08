package org.knowm.xchange.uniswap.signing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Serializes pending nonce reservation per wallet address.
 *
 * <p>For every address, the next reserved nonce is the maximum of the node-reported transaction
 * count and the locally reserved high-water mark. Reservation is atomic per address, so concurrent
 * order placement from one process cannot collide on a nonce. A reserved nonce that is never
 * broadcast leaves a harmless gap on chain.
 */
public final class NonceManager {

  private final Map<String, AtomicLong> localHighWater = new ConcurrentHashMap<>();

  /**
   * Reserves and returns the next nonce for {@code address}, given the node-reported pending
   * transaction count.
   */
  public long reserve(String address, BigIntegerSupplier onChainCount) {
    String key = normalize(address);
    long observed = onChainCount.get().longValueExact();
    AtomicLong highWater =
        localHighWater.computeIfAbsent(key, ignored -> new AtomicLong(0));
    // atomically: next = max(local, observed); local = next + 1
    return highWater.accumulateAndGet(observed, (local, observedCount) -> Math.max(local, observedCount) + 1) - 1;
  }

  /**
   * Observes a confirmed on-chain transaction count for {@code address}, so later reservations do
   * not reuse nonces consumed elsewhere.
   */
  public void sync(String address, long confirmedCount) {
    String key = normalize(address);
    AtomicLong highWater =
        localHighWater.computeIfAbsent(key, ignored -> new AtomicLong(0));
    highWater.accumulateAndGet(confirmedCount, (local, confirmed) -> Math.max(local, confirmed + 1));
  }

  /** Drops all local nonce state (for tests). */
  public void clear() {
    localHighWater.clear();
  }

  private static String normalize(String address) {
    return address.toLowerCase();
  }

  /** Lazily fetched on-chain transaction count, so the node is queried only when needed. */
  @FunctionalInterface
  public interface BigIntegerSupplier extends Supplier<java.math.BigInteger> {}
}
