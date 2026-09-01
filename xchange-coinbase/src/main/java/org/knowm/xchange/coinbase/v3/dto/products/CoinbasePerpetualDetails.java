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
  private final BigDecimal maxLeverage;

  @JsonCreator
  public CoinbasePerpetualDetails(
      @JsonProperty("funding_rate") String fundingRate,
      @JsonProperty("funding_time") String fundingTime,
      @JsonProperty("max_leverage") String maxLeverage) {
    this.fundingRate = CoinbaseFutureProductDetails.parseBigDecimal(fundingRate);
    this.fundingTime = CoinbaseFutureProductDetails.parseInstant(fundingTime);
    this.maxLeverage = CoinbaseFutureProductDetails.parseBigDecimal(maxLeverage);
  }

  /**
   * Legacy constructor for perpetual payloads without max-leverage metadata.
   *
   * @deprecated use the full-field constructor so max-leverage metadata can be retained
   */
  @Deprecated
  public CoinbasePerpetualDetails(String fundingRate, String fundingTime) {
    this(fundingRate, fundingTime, null);
  }

  @Override
  public String toString() {
    return "CoinbasePerpetualDetails [fundingRate=" + fundingRate
        + ", fundingTime=" + fundingTime + "]";
  }
}

