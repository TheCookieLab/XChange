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

  /** Sentinel for endpoints whose continuation state is not representable here. */
  public static final GateioPageCursor NONE = new GateioPageCursor(-1, -1, null, -1);

  public static GateioPageCursor page(int page) {
    if (page < 1) {
      throw new IllegalArgumentException("page must be >= 1");
    }
    return new GateioPageCursor(page, -1, null, -1);
  }

  public static GateioPageCursor offset(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be >= 0");
    }
    return new GateioPageCursor(-1, offset, null, -1);
  }

  public static GateioPageCursor afterId(String lastId) {
    if (lastId == null || lastId.isEmpty()) {
      throw new IllegalArgumentException("lastId must not be empty");
    }
    return new GateioPageCursor(-1, -1, lastId, -1);
  }

  public static GateioPageCursor since(long lastTimestamp) {
    return new GateioPageCursor(-1, -1, null, lastTimestamp);
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
