package org.knowm.xchange.okx.dto.trade;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Typed pagination parameters following the OKX v5 {@code before}/{@code after} cursor semantics.
 *
 * <p>{@code after} returns records earlier than the given ID; {@code before} returns records newer
 * than the given ID. The {@code limit} is capped at the OKX documented maximum of {@value
 * #MAX_LIMIT} so callers can never request more than the server will return in a single page.
 *
 * <p>To page forward through a history, advance the {@code after} cursor to the last record ID of
 * the previous page (see {@link #advanceAfter(String)}).
 */
@Getter
@EqualsAndHashCode
public class OkxPageParams {

  /** Server-side default page size used when no limit is supplied. */
  public static final int DEFAULT_LIMIT = 100;

  /** The maximum number of records OKX returns for a single page. */
  public static final int MAX_LIMIT = 100;

  private final String after;
  private final String before;
  private final int limit;

  /**
   * @param after records earlier than this ID, or {@code null}
   * @param before records newer than this ID, or {@code null}
   * @param limit page size; clamped to the range {@code [1, MAX_LIMIT]} (non-positive values fall
   *     back to {@link #DEFAULT_LIMIT})
   */
  public OkxPageParams(String after, String before, int limit) {
    this.after = after;
    this.before = before;
    this.limit = clamp(limit);
  }

  /** A first-page request that only bounds the page size. */
  public static OkxPageParams of(int limit) {
    return new OkxPageParams(null, null, limit);
  }

  /**
   * Returns a copy of these params with the {@code after} cursor advanced to {@code lastRecordId},
   * for fetching the next (older) page. A caller-supplied {@code before} bound is preserved so
   * subsequent pages stay inside the requested range.
   */
  public OkxPageParams advanceAfter(String lastRecordId) {
    return new OkxPageParams(lastRecordId, before, limit);
  }

  private static int clamp(int limit) {
    if (limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }
}
