package info.bitrich.xchangestream.cryptocom;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bounded, insertion-ordered deduplicator for replayed private events.
 *
 * <p>When the Crypto.com user feed reconnects it replays recent order/fill/balance events with
 * their original stable identity (fill {@code trade_id}, per-order {@code update_time}, balance
 * state snapshot). Subscribers must not see the same event twice, so each processed event records
 * a stable key and duplicates are filtered. The cache is bounded - the Crypto.com replay window is
 * short, so {@value #DEFAULT_MAX_ENTRIES} entries cover it comfortably while keeping memory flat.
 *
 * <p>A {@link ConcurrentMap} holds the keys while an explicit insertion-order {@link Deque}
 * tracks the order for bounded FIFO eviction (re-inserting an existing key neither reorders nor
 * counts against the bound, exactly like insertion-ordered {@code LinkedHashMap} semantics).
 * Every mutation happens inside the synchronized methods, so the instance stays thread-safe.
 */
public final class CryptoComStreamingEventDeduplicator {

  /** Default capacity; Crypto.com replays at most a few seconds of user events on reconnect. */
  public static final int DEFAULT_MAX_ENTRIES = 4096;

  private final int maxEntries;

  private final ConcurrentMap<String, Boolean> seen = new ConcurrentHashMap<>();

  /** Insertion order of the keys in {@link #seen}; keeps eviction strictly FIFO and bounded. */
  private final Deque<String> insertionOrder = new ArrayDeque<>();

  public CryptoComStreamingEventDeduplicator() {
    this(DEFAULT_MAX_ENTRIES);
  }

  public CryptoComStreamingEventDeduplicator(int maxEntries) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
    }
    this.maxEntries = maxEntries;
  }

  /**
   * Records the key and returns {@code true} when it was already seen (duplicate replay). A key
   * seen once within the bounded window is not a duplicate.
   */
  public synchronized boolean isDuplicate(String key) {
    if (seen.putIfAbsent(key, Boolean.TRUE) != null) {
      return true;
    }
    insertionOrder.addLast(key);
    while (insertionOrder.size() > maxEntries) {
      seen.remove(insertionOrder.removeFirst());
    }
    return false;
  }

  /** Number of distinct keys currently recorded. */
  public synchronized int size() {
    return seen.size();
  }

  /** Clears all recorded keys. */
  public synchronized void clear() {
    seen.clear();
    insertionOrder.clear();
  }
}