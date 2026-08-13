package org.knowm.xchange.gateio.dto;

import lombok.Value;

/**
 * Terminal state of a bounded Gate API v4 pagination iteration.
 */
public enum GateioIterationStop {
  /** The collection was fully consumed (the last page had no next cursor). */
  COMPLETED,
  /** The caller's result ceiling was reached; {@link GateioContinuation#getNextCursor()} allows resuming. */
  MAX_RESULTS,
  /** The provider returned a cursor already visited — iteration stopped instead of looping forever. */
  REPEATED_CURSOR,
  /** The provider returned an empty page with a next cursor — no progress; iteration stopped. */
  NO_PROGRESS
}
