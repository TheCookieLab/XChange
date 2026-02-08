package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * Coinbase Advanced Trade (v3) futures product details.
 *
 * <p>This DTO intentionally models only a subset of the response fields that are relevant for
 * futures/perpetual runtime validation and cost modeling (for example funding and margin rates).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFutureProductDetails {

  private final String contractRootUnit;
  private final BigDecimal fundingRate;
  private final Instant fundingTime;
  private final CoinbaseMarginRate intradayMarginRate;
  private final CoinbaseMarginRate overnightMarginRate;
  private final CoinbasePerpetualDetails perpetualDetails;

  @JsonCreator
  public CoinbaseFutureProductDetails(
      @JsonProperty("contract_root_unit") String contractRootUnit,
      @JsonProperty("funding_rate") String fundingRate,
      @JsonProperty("funding_time") String fundingTime,
      @JsonProperty("intraday_margin_rate") CoinbaseMarginRate intradayMarginRate,
      @JsonProperty("overnight_margin_rate") CoinbaseMarginRate overnightMarginRate,
      @JsonProperty("perpetual_details") CoinbasePerpetualDetails perpetualDetails) {
    this.contractRootUnit = contractRootUnit;
    this.fundingRate = parseBigDecimal(fundingRate);
    this.fundingTime = parseInstant(fundingTime);
    this.intradayMarginRate = intradayMarginRate;
    this.overnightMarginRate = overnightMarginRate;
    this.perpetualDetails = perpetualDetails;
  }

  static BigDecimal parseBigDecimal(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(normalized);
    } catch (NumberFormatException ignore) {
      return null;
    }
  }

  static Instant parseInstant(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    try {
      return Instant.parse(normalized);
    } catch (Exception ignore) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "CoinbaseFutureProductDetails [contractRootUnit=" + contractRootUnit
        + ", fundingRate=" + fundingRate
        + ", fundingTime=" + fundingTime
        + ", intradayMarginRate=" + intradayMarginRate
        + ", overnightMarginRate=" + overnightMarginRate
        + ", perpetualDetails=" + perpetualDetails + "]";
  }
}

