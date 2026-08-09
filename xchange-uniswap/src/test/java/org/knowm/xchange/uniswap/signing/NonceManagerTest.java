package org.knowm.xchange.uniswap.signing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Per-address nonce serialization (acceptance criterion AC3). */
class NonceManagerTest {

  private static final String WALLET = "0x1111111111111111111111111111111111111111";

  @Test
  void reservesSequentialNoncesFromTheObservedCount() {
    NonceManager manager = new NonceManager();
    assertThat(manager.reserve(WALLET, () -> BigInteger.valueOf(5))).isEqualTo(5);
    assertThat(manager.reserve(WALLET, () -> BigInteger.valueOf(5))).isEqualTo(6);
    assertThat(manager.reserve(WALLET, () -> BigInteger.valueOf(7))).isEqualTo(7);
  }

  @Test
  void concurrentReservationsNeverCollide() throws Exception {
    NonceManager manager = new NonceManager();
    int threads = 16;
    int perThread = 100;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<List<Long>>> futures = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      futures.add(
          pool.submit(
              () -> {
                start.await();
                List<Long> nonces = new ArrayList<>();
                for (int i = 0; i < perThread; i++) {
                  nonces.add(manager.reserve(WALLET, () -> BigInteger.ZERO));
                }
                return nonces;
              }));
    }
    start.countDown();
    java.util.Set<Long> all = ConcurrentHashMap.newKeySet();
    for (Future<List<Long>> future : futures) {
      all.addAll(future.get());
    }
    pool.shutdown();
    assertThat(all).hasSize(threads * perThread);
  }

  @Test
  void syncRaisesTheHighWaterMarkAboveObservedCount() {
    NonceManager manager = new NonceManager();
    manager.reserve(WALLET, () -> BigInteger.valueOf(10));
    manager.sync(WALLET, 12);
    assertThat(manager.reserve(WALLET, () -> BigInteger.valueOf(12))).isEqualTo(13);
  }
}
