package org.knowm.xchange.okex.dto.account;

import org.knowm.xchange.okx.dto.account.OkxSetPositionModeResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxSetPositionModeResponse} instead.
 */
@Deprecated
public class OkexSetPositionModeResponse {

  private final OkxSetPositionModeResponse delegate;

  public OkexSetPositionModeResponse(OkxSetPositionModeResponse delegate) {
    this.delegate = delegate;
  }

  public String getPositionMode() {
    return delegate.getPositionMode();
  }

  public String getAccountLevel() {
    return delegate.getAccountLevel();
  }
}
