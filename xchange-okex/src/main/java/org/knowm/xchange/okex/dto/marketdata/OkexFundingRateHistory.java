package org.knowm.xchange.okex.dto.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory} instead.
 */
@Deprecated
public class OkexFundingRateHistory {

  private final OkxFundingRateHistory delegate;

  public OkexFundingRateHistory(OkxFundingRateHistory delegate) {
    this.delegate = delegate;
  }

  public String getInstType() {
    return delegate.getInstType();
  }

  public Instrument getInstrument() {
    return delegate.getInstrument();
  }

  public BigDecimal getPredictedFundingRate() {
    return delegate.getPredictedFundingRate();
  }

  public BigDecimal getFundingRate() {
    return delegate.getFundingRate();
  }

  public Instant getFundingTime() {
    return delegate.getFundingTime();
  }

  public String getMethod() {
    return delegate.getMethod();
  }
}
