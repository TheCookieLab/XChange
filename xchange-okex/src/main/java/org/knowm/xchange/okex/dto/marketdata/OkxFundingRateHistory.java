package org.knowm.xchange.okex.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import org.knowm.xchange.instrument.Instrument;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory} instead.
 */
@Deprecated
public class OkxFundingRateHistory {

  private final org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory delegate;

  public OkxFundingRateHistory(
      @JsonProperty("instType") String instType,
      @JsonProperty("instId") String instrument,
      @JsonProperty("fundingRate") BigDecimal predictedFundingRate,
      @JsonProperty("realizedRate") BigDecimal fundingRate,
      @JsonProperty("fundingTime") long fundingTime,
      @JsonProperty("method") String method) {
    this.delegate =
        new org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory(
            instType, instrument, predictedFundingRate, fundingRate, fundingTime, method);
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
