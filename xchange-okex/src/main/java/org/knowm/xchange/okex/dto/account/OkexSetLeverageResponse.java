package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxSetLeverageResponse} instead.
 */
@Deprecated
public class OkexSetLeverageResponse {

  private final OkxSetLeverageResponse delegate;

  @JsonCreator
  public OkexSetLeverageResponse(OkxSetLeverageResponse delegate) {
    this.delegate = delegate;
  }

  public String getMarginMode() {
    return delegate.getMarginMode();
  }

  public String getPositionSide() {
    return delegate.getPositionSide();
  }

  public String getInstrumentId() {
    return delegate.getInstrumentId();
  }

  public String getLeverage() {
    return delegate.getLeverage();
  }
}
