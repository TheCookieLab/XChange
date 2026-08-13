package org.knowm.xchange.gateio.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

  /**
   * Fetches one page of a Gate API v4 collection for the given cursor ({@code null} = first page).
   *
   * <p>The fetcher must keep its page size constant for every page of a
   * collection (Gate's page-number endpoints address records relative to the
   * limit, so a changing limit shifts the grid and duplicates or skips
   * records). It honors a cursor's {@link GateioPageCursor#getSkip()} by
   * re-fetching the referenced page and dropping the consumed prefix.
   */
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
   * @return accumulated items plus the stop reason; the result never exceeds {@code maxResults}.
   *     {@link GateioIterationStop#MAX_RESULTS} carries a resumable {@link
   *     GateioContinuation#getNextCursor()}. When the ceiling cuts a page, the cursor's in-page
   *     skip is advanced so the unconsumed tail of that page is returned by a resume instead of
   *     being lost.
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
      List<T> pageItems = page.getItems() == null ? Collections.emptyList() : page.getItems();
      items.addAll(pageItems);
      GateioPageCursor next = page.getNextCursor();
      if (items.size() > maxResults) {
        // the page that crosses the ceiling is cut; resume mid-page so nothing is lost
        int kept = maxResults - (items.size() - pageItems.size());
        return new GateioContinuation<>(
            new ArrayList<>(items.subList(0, maxResults)),
            GateioIterationStop.MAX_RESULTS,
            resumeAfter(cursor, next, kept));
      }
      if (next == null) {
        return new GateioContinuation<>(items, GateioIterationStop.COMPLETED, null);
      }
      if (items.size() >= maxResults) {
        return new GateioContinuation<>(items, GateioIterationStop.MAX_RESULTS, next);
      }
      if (pageItems.isEmpty()) {
        return new GateioContinuation<>(items, GateioIterationStop.NO_PROGRESS, null);
      }
      if (!seenCursors.add(next)) {
        return new GateioContinuation<>(items, GateioIterationStop.REPEATED_CURSOR, null);
      }
      cursor = next;
    }
  }

  /**
   * Cursor that resumes after {@code kept} items of the current page.
   *
   * <p>Page-based collections record the in-page skip so the cut tail stays
   * reachable. Other cursor styles cannot express mid-page state and fall back
   * to the provider's next-page cursor, so a page cut there is not resumable.
   */
  private static GateioPageCursor resumeAfter(
      GateioPageCursor cursor, GateioPageCursor next, int kept) {
    if (cursor == null) {
      return GateioPageCursor.page(1).withSkip(kept);
    }
    if (cursor.isPageBased()) {
      return cursor.withSkip(cursor.getSkip() + kept);
    }
    return next;
  }
}
