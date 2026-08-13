package org.knowm.xchange.gateio.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.knowm.xchange.gateio.dto.GateioContinuation;
import org.knowm.xchange.gateio.dto.GateioIterationStop;
import org.knowm.xchange.gateio.dto.GateioPage;
import org.knowm.xchange.gateio.dto.GateioPageCursor;

/**
 * Bounded iteration policy for Gate API v4 paginated collections.
 *
 * <p>Convenience iteration must never fetch unboundedly: callers provide a
 * result ceiling, and the loop terminates on natural completion, on reaching
 * the ceiling, on a repeated cursor (provider loop), or on a no-progress
 * response (empty page that still advertises a next cursor). Cursor equality
 * is structural via {@link GateioPageCursor} value semantics.
 */
public final class GateioPagination {

  private GateioPagination() {}

  /** Fetches one page of a Gate API v4 collection for the given cursor ({@code null} = first page). */
  @FunctionalInterface
  public interface PageFetcher<T> {
    GateioPage<T> fetch(GateioPageCursor cursor) throws IOException;
  }

  /**
   * Iterates a paginated collection honoring the caller ceiling.
   *
   * @param fetcher page fetcher; receives {@code null} for the first page and the previous page's
   *     cursor afterwards
   * @param maxResults caller result ceiling; must be {@code > 0}
   * @return accumulated items plus the stop reason; {@link GateioIterationStop#MAX_RESULTS} results
   *     carry a resumable {@link GateioContinuation#getNextCursor()}
   * @throws IllegalArgumentException when {@code maxResults <= 0}
   * @throws IOException when a page fetch fails
   */
  public static <T> GateioContinuation<T> iterate(PageFetcher<T> fetcher, int maxResults)
      throws IOException {
    if (maxResults <= 0) {
      throw new IllegalArgumentException("maxResults must be > 0");
    }
    List<T> items = new ArrayList<>();
    Set<GateioPageCursor> seenCursors = new HashSet<>();
    GateioPageCursor cursor = null;
    while (true) {
      GateioPage<T> page = fetcher.fetch(cursor);
      if (page.getItems() != null) {
        items.addAll(page.getItems());
      }
      GateioPageCursor next = page.getNextCursor();
      if (next == null) {
        return new GateioContinuation<>(items, GateioIterationStop.COMPLETED, null);
      }
      if (items.size() >= maxResults) {
        return new GateioContinuation<>(items, GateioIterationStop.MAX_RESULTS, next);
      }
      if (page.getItems() == null || page.getItems().isEmpty()) {
        return new GateioContinuation<>(items, GateioIterationStop.NO_PROGRESS, next);
      }
      if (!seenCursors.add(next)) {
        return new GateioContinuation<>(items, GateioIterationStop.REPEATED_CURSOR, next);
      }
      cursor = next;
    }
  }
}
