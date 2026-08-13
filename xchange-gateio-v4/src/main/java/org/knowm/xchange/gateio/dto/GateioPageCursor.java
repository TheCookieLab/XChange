package org.knowm.xchange.gateio.dto;

import lombok.Value;

/**
 * Immutable provider-native continuation state for a Gate API v4 paginated
 * collection.
 *
 * <p>Gate's documented history endpoints use different continuation styles —
 * explicit page numbers, zero-based offsets, ID cursors, and from/to time
 * windows. A {@code GateioPageCursor} captures exactly one style's state: the
 * fields not used by that style stay at their unset sentinels ({@code -1},
 * {@code null}), so equality (and therefore loop detection in {@link
 * org.knowm.xchange.gateio.service.GateioPagination}) only compares the fields
 * that drive the next request.
 *
 * <p>Page-based cursors additionally carry {@link #getSkip()}, the number of
 * items already consumed from the referenced page's flattened result list.
 * A bounded iteration that cuts a page at the caller ceiling returns the
 * cursor with the skip advanced, so a resume re-fetches that page, drops the
 * consumed prefix, and continues without losing the unconsumed tail. The skip
 * participates in value equality: it is not sent to the provider.
 */
@Value
public class GateioPageCursor {

  /** 1-based page number for page-based endpoints; {@code -1} when unused. */
  int page;

  /** Zero-based offset for offset-based endpoints; {@code -1} when unused. */
  int offset;

  /** ID cursor (e.g. {@code last_id}) for ID-window endpoints; {@code null} when unused. */
  String lastId;

  /** Unix timestamp (seconds) marking the start of the next time window; {@code -1} when unused. */
  long lastTimestamp;

  /**
   * Items already consumed from the referenced page's flattened result list;
   * {@code 0} when unused. Only meaningful for page-based cursors.
   */
  int skip;

  /** Sentinel for endpoints whose continuation state is not representable here. */
  public static final GateioPageCursor NONE = new GateioPageCursor(-1, -1, null, -1, 0);

  public static GateioPageCursor page(int page) {
    if (page < 1) {
      throw new IllegalArgumentException("page must be >= 1");
    }
    return new GateioPageCursor(page, -1, null, -1, 0);
  }

  /** Returns a copy of this cursor with the in-page skip advanced by {@code skip} items. */
  public GateioPageCursor withSkip(int skip) {
    if (skip < 0) {
      throw new IllegalArgumentException("skip must be >= 0");
    }
    return new GateioPageCursor(page, offset, lastId, lastTimestamp, skip);
  }

  public static GateioPageCursor offset(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be >= 0");
    }
    return new GateioPageCursor(-1, offset, null, -1, 0);
  }

  public static GateioPageCursor afterId(String lastId) {
    if (lastId == null || lastId.isEmpty()) {
      throw new IllegalArgumentException("lastId must not be empty");
    }
    return new GateioPageCursor(-1, -1, lastId, -1, 0);
  }

  public static GateioPageCursor since(long lastTimestamp) {
    if (lastTimestamp < 0) {
      throw new IllegalArgumentException("lastTimestamp must be >= 0");
    }
    return new GateioPageCursor(-1, -1, null, lastTimestamp, 0);
  }

  public boolean isPageBased() {
    return page > 0;
  }

  public boolean isOffsetBased() {
    return offset >= 0;
  }

  public boolean isIdBased() {
    return lastId != null;
  }

  public boolean isTimeBased() {
    return lastTimestamp >= 0;
  }
}
