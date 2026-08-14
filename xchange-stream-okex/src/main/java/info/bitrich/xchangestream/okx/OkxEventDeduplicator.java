package info.bitrich.xchangestream.okx;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, deterministic duplicate detector for private streaming events.
 *
 * <p>OKX may re-deliver private channel events (orders, positions, fills) on reconnect or on
 * subscription overlap. This cache records a canonical key per event and reports whether the same
 * event was already seen. Keys are evicted in insertion order once the configured cap is exceeded,
 * so the memory footprint stays bounded while long-lived streams can still detect repeats of
 * recent events.
 *
 * <p>Thread-safe; the per-key dedupe decision is atomic with the recording of the key.
 *
 * <p>Package-private by design: an internal deterministic seam, injectable for offline tests.
 */
final class OkxEventDeduplicator {

  private final int maxSize;
  private final LinkedHashMap<String, Boolean> seen;

  OkxEventDeduplicator(int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize must be positive, got " + maxSize);
    }
    this.maxSize = maxSize;
    this.seen =
        new LinkedHashMap<String, Boolean>(maxSize, 0.75f, false) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > maxSize;
          }
        };
  }

  /**
   * Returns {@code true} if the key was recorded by an earlier call (i.e. the event was already
   * delivered) and {@code false} for a new event, which is recorded before returning.
   */
  synchronized boolean isDuplicate(String key) {
    if (key == null) {
      return false;
    }
    if (seen.containsKey(key)) {
      return true;
    }
    seen.put(key, Boolean.TRUE);
    return false;
  }

  /** Current number of recorded keys (test seam). */
  synchronized int size() {
    return seen.size();
  }

  synchronized void clear() {
    seen.clear();
  }
}
