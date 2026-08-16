package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for {@link CryptoComStreamingEventDeduplicator}: first delivery is not a
 * duplicate, the same stable key replays as a duplicate, distinct keys are independent, the cache
 * is bounded, and clears reset the recorded window.
 */
public class CryptoComStreamingEventDeduplicatorTest {

  @Test
  public void testFirstDeliveryIsNotDuplicateSecondIs() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator();

    assertThat(deduplicator.isDuplicate("user.trade.BTC_USDT.5533001")).isFalse();
    assertThat(deduplicator.isDuplicate("user.trade.BTC_USDT.5533001")).isTrue();
    assertThat(deduplicator.size()).isEqualTo(1);
  }

  @Test
  public void testDistinctKeysAreIndependent() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator();

    assertThat(deduplicator.isDuplicate("user.order.BTC_USDT.18342311.1785085695512")).isFalse();
    assertThat(deduplicator.isDuplicate("user.trade.BTC_USDT.5533001")).isFalse();
    assertThat(deduplicator.isDuplicate("user.balance.1.2.3")).isFalse();
    assertThat(deduplicator.size()).isEqualTo(3);
  }

  @Test
  public void testCacheIsBoundedWithFifoEviction() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator(2);

    assertThat(deduplicator.isDuplicate("key-1")).isFalse();
    assertThat(deduplicator.isDuplicate("key-2")).isFalse();
    assertThat(deduplicator.isDuplicate("key-3")).isFalse();
    assertThat(deduplicator.size()).isEqualTo(2);

    // the eldest key was evicted, so a re-issued key is a new event again
    assertThat(deduplicator.isDuplicate("key-1")).isFalse();
    assertThat(deduplicator.size()).isEqualTo(2);
    assertThat(deduplicator.isDuplicate("key-2")).isFalse();
    assertThat(deduplicator.size()).isEqualTo(2);

    // while a key still inside the window is still recognized as a duplicate
    assertThat(deduplicator.isDuplicate("key-1")).isTrue();
    assertThat(deduplicator.size()).isEqualTo(2);
  }

  @Test
  public void testClearResetsTheRecordedWindow() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator();

    assertThat(deduplicator.isDuplicate("key-1")).isFalse();
    deduplicator.clear();
    assertThat(deduplicator.size()).isZero();
    assertThat(deduplicator.isDuplicate("key-1")).isFalse();
  }

  @Test
  public void testCapacityMustBePositive() {
    try {
      new CryptoComStreamingEventDeduplicator(0);
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("maxEntries");
      return;
    }
    throw new AssertionError("expected IllegalArgumentException for non-positive capacity");
  }
}