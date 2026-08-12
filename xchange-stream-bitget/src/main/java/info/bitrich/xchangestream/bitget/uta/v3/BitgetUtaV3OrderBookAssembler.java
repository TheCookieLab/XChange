package info.bitrich.xchangestream.bitget.uta.v3;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Action;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3OrderBookData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3OrderBookLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Incremental order-book state machine for the Bitget UTA v3 order-book channel.
 *
 * <p>The provider sends a full {@code snapshot} first and then incremental {@code update}s that
 * carry {@code pseq} (the previous push's sequence) and a monotonic {@code seq}. Integrity is
 * verified purely by sequence continuity — the v3 protocol has no checksum. Per the official
 * documentation: the first update (pseq P, seq U) after a snapshot (seq S) must satisfy {@code P ≤
 * S ≤ U}; every subsequent update must satisfy {@code pseq == seq-of-previous-push}; an update with
 * {@code pseq=0} signals a provider sequence-space reset. The {@code books1}/{@code books5}/{@code
 * books50} topics always arrive with {@code action=snapshot} (replace-only) and never produce
 * incremental updates.
 *
 * <p>On any continuity violation the assembler resets itself and throws {@link
 * BitgetUtaV3OrderBookContinuityException}; the caller must resubscribe the channel to obtain a
 * fresh snapshot. Stale or duplicate updates (sequence not newer than the last applied one) and
 * updates arriving before the snapshot are dropped without error.
 *
 * <p>Not thread-safe: drive it from a single Netty event-loop or test thread.
 *
 * @since 5.1.0
 */
@Slf4j
public class BitgetUtaV3OrderBookAssembler {

  /** Bids sorted descending (best first). */
  private final TreeMap<BigDecimal, BitgetUtaV3OrderBookLevel> bids =
      new TreeMap<>(Comparator.reverseOrder());

  /** Asks sorted ascending (best first). */
  private final TreeMap<BigDecimal, BitgetUtaV3OrderBookLevel> asks = new TreeMap<>();

  private Long lastSeq;
  private boolean snapshotApplied;
  private boolean firstUpdateAfterSnapshot;

  /**
   * Applies a snapshot or update keyed by the push envelope's {@code action}. Throws {@link
   * BitgetUtaV3OrderBookContinuityException} and resets internal state on any continuity violation.
   *
   * @param data incoming order-book push
   * @param action envelope action ({@code snapshot} or {@code update})
   * @param subscriptionId channel subscription id, used in the failure message
   */
  public void apply(
      BitgetUtaV3OrderBookData data, BitgetUtaV3Action action, String subscriptionId) {
    if (data.getBids() == null && data.getAsks() == null) {
      return;
    }
    if (action == BitgetUtaV3Action.SNAPSHOT) {
      applySnapshot(data);
    } else {
      applyUpdate(data, subscriptionId);
    }
  }

  private void applySnapshot(BitgetUtaV3OrderBookData data) {
    bids.clear();
    asks.clear();
    replaceLevels(bids, data.getBids());
    replaceLevels(asks, data.getAsks());
    lastSeq = data.getSeq();
    snapshotApplied = true;
    firstUpdateAfterSnapshot = true;
  }

  private void applyUpdate(BitgetUtaV3OrderBookData data, String subscriptionId) {
    if (!snapshotApplied) {
      // update before any snapshot: the provider contract sends the snapshot first
      log.debug("Dropping order-book update for {} before its snapshot", subscriptionId);
      return;
    }
    // pseq=0 on an update means the provider restarted its sequence space
    if (data.getPseq() != null && data.getPseq() == 0) {
      reset();
      throw new BitgetUtaV3OrderBookContinuityException(
          subscriptionId, "provider sequence-space reset (pseq=0)");
    }
    // stale or duplicate update: never apply one not newer than the last applied seq
    if (data.getSeq() != null && lastSeq != null && data.getSeq() <= lastSeq) {
      log.debug(
          "Dropping stale order-book update for {}: seq {} <= {}",
          subscriptionId,
          data.getSeq(),
          lastSeq);
      return;
    }
    if (data.getPseq() != null && lastSeq != null) {
      boolean continuous;
      if (firstUpdateAfterSnapshot) {
        // first update (pseq P, seq U) after snapshot (seq S): P <= S <= U
        continuous =
            data.getPseq() <= lastSeq && (data.getSeq() == null || lastSeq <= data.getSeq());
      } else {
        // subsequent updates: previous push seq must match our last applied seq
        continuous = data.getPseq().equals(lastSeq);
      }
      if (!continuous) {
        reset();
        throw new BitgetUtaV3OrderBookContinuityException(
            subscriptionId,
            "sequence gap: update references pseq="
                + data.getPseq()
                + (firstUpdateAfterSnapshot ? " (after snapshot seq " : " but last applied seq ")
                + lastSeq
                + ", seq="
                + data.getSeq());
      }
    }
    firstUpdateAfterSnapshot = false;

    replaceLevels(bids, data.getBids());
    replaceLevels(asks, data.getAsks());
    if (data.getSeq() != null) {
      lastSeq = data.getSeq();
    }
  }

  private void replaceLevels(
      TreeMap<BigDecimal, BitgetUtaV3OrderBookLevel> side, List<BitgetUtaV3OrderBookLevel> levels) {
    if (levels == null) {
      return;
    }
    for (BitgetUtaV3OrderBookLevel level : levels) {
      if (level.getSize().signum() <= 0) {
        // quantity 0 deletes a level; a negative quantity is malformed and must
        // never surface as a negative-amount level in the book
        side.remove(level.getPrice());
      } else {
        side.put(level.getPrice(), level);
      }
    }
  }

  /** Whether a snapshot has been applied and the book is usable. */
  public boolean hasSnapshot() {
    return snapshotApplied;
  }

  /** Current bids, best first. */
  public List<BitgetUtaV3OrderBookLevel> getBids() {
    return new ArrayList<>(bids.values());
  }

  /** Current asks, best first. */
  public List<BitgetUtaV3OrderBookLevel> getAsks() {
    return new ArrayList<>(asks.values());
  }

  /** Last applied provider sequence, or {@code null} before the first snapshot. */
  /**
   * The sequence number of the last applied push, or {@code null} when no snapshot has been applied
   * yet (or after {@link #reset()}).
   */
  public Long getLastSeq() {
    return snapshotApplied ? lastSeq : null;
  }

  /** Drops all state so the next snapshot starts a fresh book. */
  public void reset() {
    bids.clear();
    asks.clear();
    snapshotApplied = false;
    firstUpdateAfterSnapshot = false;
  }
}
