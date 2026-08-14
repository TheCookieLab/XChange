package org.knowm.xchange.okex.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRate;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxFundingRate} instead.
 */
@Deprecated
public class OkexFundingRate {

  private final OkxFundingRate delegate;

  @JsonCreator
  public OkexFundingRate(OkxFundingRate delegate) {
    this.delegate = delegate;
  }

  /**
   * Retained legacy value constructor; builds the canonical DTO internally.
   *
   * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxFundingRate} instead.
   */
  @Deprecated
  public OkexFundingRate(
      @JsonProperty("instType") String instType,
      @JsonProperty("instId") String instId,
      @JsonProperty("fundingRate") BigDecimal fundingRate,
      @JsonProperty("nextFundingRate") BigDecimal nextFundingRate,
      @JsonProperty("fundingTime") Date fundingTime,
      @JsonProperty("nextFundingTime") Date nextFundingTime) {
    this(
        new OkxFundingRate(
            instType, instId, fundingRate, nextFundingRate, fundingTime, nextFundingTime));
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
