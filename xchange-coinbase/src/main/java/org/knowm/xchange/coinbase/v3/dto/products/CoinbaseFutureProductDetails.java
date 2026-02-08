package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Duration;
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
  private final Duration fundingInterval;
  private final CoinbaseMarginRate intradayMarginRate;
  private final CoinbaseMarginRate overnightMarginRate;
  private final CoinbasePerpetualDetails perpetualDetails;

  public CoinbaseFutureProductDetails(
      String contractRootUnit,
      String fundingRate,
      String fundingTime,
      CoinbaseMarginRate intradayMarginRate,
      CoinbaseMarginRate overnightMarginRate,
      CoinbasePerpetualDetails perpetualDetails) {
    this(contractRootUnit, fundingRate, fundingTime, intradayMarginRate, overnightMarginRate, perpetualDetails, null);
  }

  @JsonCreator
  public CoinbaseFutureProductDetails(
      @JsonProperty("contract_root_unit") String contractRootUnit,
      @JsonProperty("funding_rate") String fundingRate,
      @JsonProperty("funding_time") String fundingTime,
      @JsonProperty("intraday_margin_rate") CoinbaseMarginRate intradayMarginRate,
      @JsonProperty("overnight_margin_rate") CoinbaseMarginRate overnightMarginRate,
      @JsonProperty("perpetual_details") CoinbasePerpetualDetails perpetualDetails,
      @JsonProperty("funding_interval") String fundingInterval) {
    this.contractRootUnit = contractRootUnit;
    this.fundingRate = parseBigDecimal(fundingRate);
    this.fundingTime = parseInstant(fundingTime);
    this.fundingInterval = parseDuration(fundingInterval);
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

  static Duration parseDuration(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    // Accept ISO-8601 durations (e.g. PT1H) as well as Coinbase-style "3600s".
    if (normalized.startsWith("P") || normalized.startsWith("p")) {
      try {
        return Duration.parse(normalized.toUpperCase());
      } catch (Exception ignore) {
        return null;
      }
    }
    String lower = normalized.toLowerCase();
    if (lower.endsWith("s")) {
      lower = lower.substring(0, lower.length() - 1).trim();
    }
    try {
      return Duration.ofSeconds(Long.parseLong(lower));
    } catch (NumberFormatException ignore) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "CoinbaseFutureProductDetails [contractRootUnit=" + contractRootUnit
        + ", fundingRate=" + fundingRate
        + ", fundingTime=" + fundingTime
        + ", fundingInterval=" + fundingInterval
        + ", intradayMarginRate=" + intradayMarginRate
        + ", overnightMarginRate=" + overnightMarginRate
        + ", perpetualDetails=" + perpetualDetails + "]";
  }
}
