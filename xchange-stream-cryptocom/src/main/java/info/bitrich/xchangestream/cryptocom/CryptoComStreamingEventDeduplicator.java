package info.bitrich.xchangestream.cryptocom;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, insertion-ordered deduplicator for replayed private events.
 *
 * <p>When the Crypto.com user feed reconnects it replays recent order/fill/balance events with
 * their original stable identity (fill {@code trade_id}, per-order {@code update_time}, balance
 * state snapshot). Subscribers must not see the same event twice, so each processed event records
 * a stable key and duplicates are filtered. The cache is bounded - the Crypto.com replay window is
 * short, so {@value #DEFAULT_MAX_ENTRIES} entries cover it comfortably while keeping memory flat.
 *
 * <p>Not thread-safe: used single-threaded on the WebSocket event loop; wrap in a synchronized
 * facade when sharing across threads.
 */
public final class CryptoComStreamingEventDeduplicator {

  /** Default capacity; Crypto.com replays at most a few seconds of user events on reconnect. */
  public static final int DEFAULT_MAX_ENTRIES = 4096;

  private final int maxEntries;
  private final Map<String, Boolean> seen =
      new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
          return size() > maxEntries;
        }
      };

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
    return seen.put(key, Boolean.TRUE) != null;
  }

  /** Number of distinct keys currently recorded. */
  public synchronized int size() {
    return seen.size();
  }

  /** Clears all recorded keys. */
  public synchronized void clear() {
    seen.clear();
  }
}