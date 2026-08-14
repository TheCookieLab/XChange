package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.okx.dto.trade.OkxPageParams;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxPageParams} instead.
 */
@Deprecated
public class OkexPageParams {

  /** Server-side default page size used when no limit is supplied. */
  public static final int DEFAULT_LIMIT = 100;

  /** The maximum number of records OKX returns for a single page. */
  public static final int MAX_LIMIT = 100;

  private final OkxPageParams delegate;

  public OkexPageParams(String after, String before, int limit) {
    this.delegate = new OkxPageParams(after, before, limit);
  }

  public OkexPageParams(OkxPageParams delegate) {
    this.delegate = delegate;
  }

  public String getAfter() {
    return delegate.getAfter();
  }

  public String getBefore() {
    return delegate.getBefore();
  }

  public int getLimit() {
    return delegate.getLimit();
  }

  /** A first-page request that only bounds the page size. */
  public static OkexPageParams of(int limit) {
    return new OkexPageParams(OkxPageParams.of(limit));
  }

  /**
   * Returns a copy of these params with the {@code after} cursor advanced to {@code lastRecordId},
   * for fetching the next (older) page. The {@code before} cursor is cleared.
   */
  public OkexPageParams advanceAfter(String lastRecordId) {
    return new OkexPageParams(delegate.advanceAfter(lastRecordId));
  }

  public OkxPageParams to() {
    return delegate;
  }
}
