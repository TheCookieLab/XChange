package org.knowm.xchange.gateio.dto;

import java.util.List;
import lombok.Value;

/**
 * Result of a bounded Gate API v4 pagination iteration: the accumulated items
 * plus the terminal stop reason.
 *
 * <p>When {@link #getStop()} is {@link GateioIterationStop#MAX_RESULTS}, {@link
 * #getNextCursor()} holds the cursor for the page after the last consumed one
 * so the caller can resume; for all other stops the iteration is terminal.
 *
 * @param <T> item type of the collection
 */
@Value
public class GateioContinuation<T> {

  /** Accumulated items across all consumed pages. */
  List<T> items;

  /** Why the iteration stopped. */
  GateioIterationStop stop;

  /** Cursor to resume from, or {@code null} when the collection is exhausted or the stop is terminal. */
  GateioPageCursor nextCursor;
}
