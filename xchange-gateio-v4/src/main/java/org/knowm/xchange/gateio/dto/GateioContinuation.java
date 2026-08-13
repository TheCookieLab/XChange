package org.knowm.xchange.gateio.dto;

import java.util.List;
import lombok.Value;

/**
 * Result of a bounded Gate API v4 pagination iteration: the accumulated items
 * plus the terminal stop reason.
 *
 * <p>When {@link #getStop()} is {@link GateioIterationStop#MAX_RESULTS}, {@link
 * #getNextCursor()} holds the cursor to resume from: the provider page after
 * the last fully consumed one, or the partially consumed page itself with its
 * {@link GateioPageCursor#getSkip()} advanced when the ceiling cut it. A resume
 * re-fetches that page with the same paging configuration and drops the
 * consumed prefix, so no record is lost. For all other stops the iteration is
 * terminal.
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
