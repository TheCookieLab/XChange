package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * Coinbase Advanced Trade (v3) perpetual futures details.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePerpetualDetails {

  private final BigDecimal fundingRate;
  private final Instant fundingTime;

  @JsonCreator
  public CoinbasePerpetualDetails(
      @JsonProperty("funding_rate") String fundingRate,
      @JsonProperty("funding_time") String fundingTime) {
    this.fundingRate = CoinbaseFutureProductDetails.parseBigDecimal(fundingRate);
    this.fundingTime = CoinbaseFutureProductDetails.parseInstant(fundingTime);
  }

  @Override
  public String toString() {
    return "CoinbasePerpetualDetails [fundingRate=" + fundingRate
        + ", fundingTime=" + fundingTime + "]";
  }
}

