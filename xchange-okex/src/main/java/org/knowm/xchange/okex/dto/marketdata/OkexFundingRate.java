package org.knowm.xchange.okex.dto.marketdata;

import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRate;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxFundingRate} instead.
 */
@Deprecated
public class OkexFundingRate {

  private final OkxFundingRate delegate;

  public OkexFundingRate(OkxFundingRate delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxFundingRate to() {
    return delegate;
  }

  public String getInstType() {
    return delegate.getInstType();
  }

  public String getInstId() {
    return delegate.getInstId();
  }

  public BigDecimal getFundingRate() {
    return delegate.getFundingRate();
  }

  public BigDecimal getNextFundingRate() {
    return delegate.getNextFundingRate();
  }

  public Date getFundingTime() {
    return delegate.getFundingTime();
  }

  public Date getNextFundingTime() {
    return delegate.getNextFundingTime();
  }
}
