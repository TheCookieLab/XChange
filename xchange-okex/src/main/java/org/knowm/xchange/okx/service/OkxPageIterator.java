package org.knowm.xchange.okx.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.trade.OkxPageParams;

/**
 * Deterministic, offline-testable helper for iterating OKX v5 cursor-paginated history endpoints.
 *
 * <p>Given a page-fetch function, it accumulates items across pages, stopping when:
 *
 * <ul>
 *   <li>a page returns {@code null} or an empty batch (end of data);
 *   <li>a page returns fewer items than its {@link OkxPageParams#getLimit() limit} (a partial page
 *       is the final page);
 *   <li>a page returns the same last record ID as the previous page (no progress);
 *   <li>a record ID cannot be extracted from the last item (cannot advance the cursor); or
 *   <li>the maximum page bound is reached while the last fetched page is still full — an {@link
 *       OkxException} is thrown because more records may exist beyond the ceiling and silently
 *       returning a truncated history would hide them.
 * </ul>
 *
 * <p>The caller supplies the per-item ID extractor matching the endpoint's pagination key (for
 * example {@code OkxOrderDetails::getOrderId} or {@code OkxFill::getBillId}); the cursor is
 * advanced with {@link OkxPageParams#advanceAfter(String)}.
 */
public final class OkxPageIterator {

  /** Default ceiling on the number of pages fetched when no bound is supplied. */
  public static final int DEFAULT_MAX_PAGES = 10;

  private OkxPageIterator() {}

  /**
   * Page-fetch function that may throw a checked {@link IOException}, as the OKX HTTP call it wraps
   * does.
   *
   * @param <T> the item type of a page
   */
  @FunctionalInterface
  public interface ThrowingPageFetcher<T> {
    List<T> apply(OkxPageParams params) throws IOException;
  }

  /**
   * Page-fetch function returning the full page {@link OkxResponse} so callers can validate the
   * business code before accumulating items; may throw a checked {@link IOException} as the OKX
   * HTTP call it wraps does.
   *
   * @param <T> the item type of a page
   */
  @FunctionalInterface
  public interface ThrowingPageResponseFetcher<T> {
    OkxResponse<List<T>> apply(OkxPageParams params) throws IOException;
  }

  /**
   * Fetches all items reachable from {@code initial} using {@link #DEFAULT_MAX_PAGES} pages.
   *
   * @param pageFetcher returns one page of items for the given params; must not be {@code null}
   * @param idExtractor extracts the pagination key from a single item; must not be {@code null}
   * @param initial the first page's params
   * @throws IOException if the page fetcher throws
   */
  public static <T> List<T> fetchAll(
      ThrowingPageFetcher<T> pageFetcher, Function<T, String> idExtractor, OkxPageParams initial)
      throws IOException {
    return fetchAll(pageFetcher, idExtractor, initial, DEFAULT_MAX_PAGES);
  }

  /**
   * Fetches all items reachable from {@code initial} up to {@code maxPages} page fetches.
   *
   * @param pageFetcher returns one page of items for the given params; must not be {@code null}
   * @param idExtractor extracts the pagination key from a single item; must not be {@code null}
   * @param initial the first page's params
   * @param maxPages the maximum number of pages to fetch; must be positive
   * @throws IOException if the page fetcher throws
   * @throws OkxException if the page bound is exhausted while the last fetched page is still full
   *     (more records may exist beyond the ceiling)
   */
  public static <T> List<T> fetchAll(
      ThrowingPageFetcher<T> pageFetcher,
      Function<T, String> idExtractor,
      OkxPageParams initial,
      int maxPages)
      throws IOException {
    Objects.requireNonNull(idExtractor, "idExtractor");
    if (maxPages <= 0) {
      throw new IllegalArgumentException("maxPages must be positive");
    }

    List<T> items = new ArrayList<>();
    OkxPageParams page = initial;
    String previousLastId = null;

    for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
      List<T> batch = pageFetcher.apply(page);
      if (batch == null || batch.isEmpty()) {
        return items;
      }

      String lastId = idExtractor.apply(batch.get(batch.size() - 1));
      if (lastId == null) {
        // Cannot extract a cursor for the next page; consume what we have and stop.
        items.addAll(batch);
        return items;
      }
      if (lastId.equals(previousLastId)) {
        // A full batch that repeats the previous last record means no forward progress.
        return items;
      }

      items.addAll(batch);
      previousLastId = lastId;

      // Only keep iterating while the page returned a full batch (a partial page is the end).
      if (batch.size() < page.getLimit()) {
        return items;
      }
      page = page.advanceAfter(lastId);
    }

    // The loop exhausted the page bound, so every fetched page was full and more records may
    // exist; fail loudly rather than silently returning truncated history.
    throw new OkxException(
        "OKX history pagination reached its ceiling of "
            + maxPages
            + " pages while the last page was still full; narrow the query or raise the page"
            + " bound to avoid silently truncated results",
        0);
  }
}
