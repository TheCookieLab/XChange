package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxSetPositionModeResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxSetPositionModeResponse} instead.
 */
@Deprecated
public class OkexSetPositionModeResponse {

  private final OkxSetPositionModeResponse delegate;

  @JsonCreator
  public OkexSetPositionModeResponse(OkxSetPositionModeResponse delegate) {
    this.delegate = delegate;
  }

  public String getPositionMode() {
    return delegate.getPositionMode();
  }

  /** Returns the position mode using the upstream DTO accessor name. */
  public String getPosMode() {
    return getPositionMode();
  }

  public String getAccountLevel() {
    return delegate.getAccountLevel();
  }
}
