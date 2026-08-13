package org.knowm.xchange.gateio.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * One page of a Gate API v4 paginated collection as returned by a page
 * fetcher: the page items plus the cursor for the next request.
 *
 * <p>A {@code null} {@link #getNextCursor()} signals the last page. An empty
 * item list combined with a non-null cursor signals a provider no-progress
 * response and is rejected by {@link org.knowm.xchange.gateio.service.GateioPagination}.
 *
 * @param <T> item type of the page
 */
@Value
@Builder
public class GateioPage<T> {

  /** Items on this page; may be empty. */
  List<T> items;

  /** Cursor for the next page, or {@code null} when the collection is exhausted. */
  GateioPageCursor nextCursor;

  public boolean hasNext() {
    return nextCursor != null;
  }
}
