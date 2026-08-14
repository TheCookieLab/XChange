package info.bitrich.xchangestream.okx;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Continuity guard for the OKX {@code books} (full order-book) channel.
 *
 * <p>The OKX books channel streams an initial snapshot followed by incremental updates. Every
 * message carries a {@code seqId} (and {@code prevSeqId}) that must advance by exactly one per
 * message and, while the field was active, a {@code checksum} over the reconstructed book. This
 * class enforces both invariants per instrument:
 *
 * <ul>
 *   <li>updates whose {@code seqId} is not strictly greater than the last applied sequence are
 *       dropped (duplicates or out-of-order deliveries);
 *   <li>a {@code seqId} jump (gap) or a checksum mismatch marks the book for rebuild: the caller
 *       re-subscribes the channel and drops all updates until a fresh snapshot resets the state.
 * </ul>
 *
 * <p>The checksum algorithm follows the OKX v5 documentation: concatenate every level as {@code
 * price:size} — bids in descending price order followed by asks in ascending price order, with no
 * separator between levels — and compute the CRC32 (java.util.zip) of the UTF-8 encoded string. The
 * checksum in an update message covers the book as it stands after applying that update. OKX
 * deprecated the checksum field on 2026-06-23 and now always sends {@code 0}; a zero or absent
 * checksum disables verification and the sequence gate becomes the sole integrity check.
 *
 * <p>The {@link Gate} returned by {@link #gateUpdate(String, JsonNode)} must be evaluated on the
 * same thread that applies the message; the netty event loop satisfies this naturally because all
 * messages of one channel are processed sequentially.
 *
 * <p>Package-private by design: it is an internal continuity seam, kept deterministic and
 * injectable so offline tests can simulate duplicates, gaps and checksum mismatches.
 */
final class OkxBookContinuity {

  private static final Logger LOG = LoggerFactory.getLogger(OkxBookContinuity.class);

  /** Sentinel for an absent or unparseable sequence number. */
  static final long UNKNOWN_SEQ = Long.MIN_VALUE;

  /** Outcome of the continuity gate applied to one books-channel message. */
  enum Gate {
    /** The message is the next expected update and passes checksum verification: apply it. */
    ACCEPT,
    /**
     * Duplicate/out-of-order update ({@code seqId <=} last applied) or rebuilding: drop silently.
     */
    DROP_STALE,
    /** Sequence gap or checksum mismatch: drop the message and trigger a channel rebuild. */
    REBUILD
  }

  /** A price level exactly as received from the wire, preserving the raw string representation. */
  private static final class RawLevel {
    final String price;
    final String size;

    RawLevel(String price, String size) {
      this.price = price;
      this.size = size;
    }
  }

  /** Per-instrument book state: sequence tracking plus the raw levels needed for checksums. */
  private static final class BookState {
    long lastSeqId = UNKNOWN_SEQ;
    boolean rebuilding;
    final NavigableMap<BigDecimal, RawLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    final NavigableMap<BigDecimal, RawLevel> asks = new TreeMap<>();
  }

  private final ConcurrentMap<String, BookState> states = new ConcurrentHashMap<>();

  /** Resets the instrument state from a freshly received snapshot and stores its levels. */
  void snapshot(String instId, JsonNode dataElement) {
    BookState state = new BookState();
    state.lastSeqId = sequenceOf(dataElement);
    applyLevels(state, dataElement);
    states.put(instId, state);
  }

  /**
   * Gates one incremental update for the instrument.
   *
   * @param instId channel instrument id
   * @param dataElement the single data entry of the books-channel message
   * @return the gate outcome; only {@link Gate#ACCEPT} requires the caller to apply the message
   */
  Gate gateUpdate(String instId, JsonNode dataElement) {
    BookState state = states.get(instId);
    if (state == null) {
      // Updates before any snapshot: the book was never initialized, ask for a fresh one.
      LOG.warn("No snapshot seen for {} before update, requesting rebuild.", instId);
      markRebuilding(instId);
      return Gate.REBUILD;
    }
    if (state.rebuilding) {
      return Gate.DROP_STALE;
    }

    long seqId = sequenceOf(dataElement);
    if (seqId != UNKNOWN_SEQ && state.lastSeqId != UNKNOWN_SEQ) {
      if (seqId <= state.lastSeqId) {
        LOG.debug(
            "Dropping stale/duplicate book update for {}: seqId {} <= last {}",
            instId,
            seqId,
            state.lastSeqId);
        return Gate.DROP_STALE;
      }
      if (seqId > state.lastSeqId + 1) {
        LOG.warn(
            "Book sequence gap for {}: expected {} but got {}, requesting rebuild.",
            instId,
            state.lastSeqId + 1,
            seqId);
        markRebuilding(instId);
        return Gate.REBUILD;
      }
    }

    BookState candidate = copy(state);
    applyLevels(candidate, dataElement);
    long checksum = checksumOf(dataElement);
    if (checksum != 0 && checksum != checksum(candidate)) {
      LOG.warn(
          "Book checksum mismatch for {}: message {} != computed {}, requesting rebuild.",
          instId,
          checksum,
          checksum(candidate));
      markRebuilding(instId);
      return Gate.REBUILD;
    }

    candidate.lastSeqId = seqId != UNKNOWN_SEQ ? seqId : candidate.lastSeqId;
    states.put(instId, candidate);
    return Gate.ACCEPT;
  }

  /** Marks the instrument as rebuilding; subsequent updates are dropped until a new snapshot. */
  void markRebuilding(String instId) {
    BookState state = states.get(instId);
    if (state != null) {
      state.rebuilding = true;
    }
  }

  boolean isRebuilding(String instId) {
    BookState state = states.get(instId);
    return state != null && state.rebuilding;
  }

  /** Number of tracked levels for the instrument (test seam). */
  int levelCount(String instId) {
    BookState state = states.get(instId);
    if (state == null) {
      return 0;
    }
    return state.bids.size() + state.asks.size();
  }

  /**
   * Computes the OKX book checksum for the given raw bid/ask level arrays: CRC32 over {@code
   * price:size} of bids (highest price first) concatenated with {@code price:size} of asks (lowest
   * price first), no separator between levels.
   *
   * @return unsigned CRC32 value in the range {@code [0, 2^32-1]}
   */
  static long checksum(JsonNode bids, JsonNode asks) {
    CRC32 crc = new CRC32();
    appendLevels(crc, bids);
    appendLevels(crc, asks);
    return crc.getValue();
  }

  private static void appendLevels(CRC32 crc, JsonNode levels) {
    if (levels == null || !levels.isArray()) {
      return;
    }
    for (JsonNode level : levels) {
      if (!level.isArray() || level.size() < 2) {
        continue;
      }
      crc.update(
          (level.get(0).asText() + ":" + level.get(1).asText()).getBytes(StandardCharsets.UTF_8));
    }
  }

  private static long checksum(BookState state) {
    CRC32 crc = new CRC32();
    for (RawLevel level : state.bids.values()) {
      crc.update((level.price + ":" + level.size).getBytes(StandardCharsets.UTF_8));
    }
    for (RawLevel level : state.asks.values()) {
      crc.update((level.price + ":" + level.size).getBytes(StandardCharsets.UTF_8));
    }
    return crc.getValue();
  }

  private static long sequenceOf(JsonNode dataElement) {
    if (dataElement == null || !dataElement.hasNonNull("seqId")) {
      return UNKNOWN_SEQ;
    }
    String text = dataElement.get("seqId").asText();
    if (text.isEmpty()) {
      return UNKNOWN_SEQ;
    }
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      LOG.warn("Unparseable seqId '{}' in books message.", text);
      return UNKNOWN_SEQ;
    }
  }

  private static long checksumOf(JsonNode dataElement) {
    if (dataElement == null || !dataElement.hasNonNull("checksum")) {
      return 0;
    }
    long value;
    try {
      value = Long.parseLong(dataElement.get("checksum").asText());
    } catch (NumberFormatException e) {
      LOG.warn("Unparseable checksum in books message, disabling verification.");
      return 0;
    }
    // Normalize a signed 32-bit representation (some feeds emit negative checksums) to unsigned.
    return value < 0 ? value + (1L << 32) : value;
  }

  private static void applyLevels(BookState state, JsonNode dataElement) {
    applyLevels(state.bids, dataElement.get("bids"));
    applyLevels(state.asks, dataElement.get("asks"));
  }

  private static void applyLevels(NavigableMap<BigDecimal, RawLevel> target, JsonNode levels) {
    if (levels == null || !levels.isArray()) {
      return;
    }
    for (JsonNode level : levels) {
      if (!level.isArray() || level.size() < 2) {
        continue;
      }
      String priceText = level.get(0).asText();
      String sizeText = level.get(1).asText();
      if (priceText.isEmpty()) {
        continue;
      }
      BigDecimal price;
      try {
        price = new BigDecimal(priceText);
      } catch (NumberFormatException e) {
        LOG.debug("Skipping book level with unparseable price '{}'.", priceText);
        continue;
      }
      if ("0".equals(sizeText)) {
        target.remove(price);
      } else if (!sizeText.isEmpty()) {
        target.put(price, new RawLevel(priceText, sizeText));
      }
      // Empty size means "no change" and the level is left untouched.
    }
  }

  private static BookState copy(BookState source) {
    BookState copy = new BookState();
    copy.lastSeqId = source.lastSeqId;
    copy.rebuilding = source.rebuilding;
    copy.bids.putAll(source.bids);
    copy.asks.putAll(source.asks);
    return copy;
  }
}
