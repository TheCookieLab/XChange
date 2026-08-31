package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Current Advanced Trade futures-product metadata.
 *
 * <p>All numeric values are exchange observations rather than application assumptions. Missing
 * values are represented by {@code null}; callers must validate completeness before using metadata
 * for sizing or order submission.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFutureProductDetails {

  private final String venue;
  private final String contractCode;
  private final String contractExpiry;

  @Getter(AccessLevel.NONE)
  private final String contractSize;

  private final String contractRootUnit;
  private final String groupDescription;
  private final String contractExpiryTimezone;
  private final String groupShortDescription;
  private final String riskManagedBy;
  private final String contractExpiryType;
  private final CoinbasePerpetualDetails perpetualDetails;
  private final String contractDisplayName;
  private final Long timeToExpiryMs;
  private final Boolean nonCrypto;
  private final String contractExpiryName;
  private final Boolean twentyFourBySeven;
  private final Duration fundingInterval;
  private final BigDecimal openInterest;

  @Getter(AccessLevel.NONE)
  private final String fundingRate;

  @Getter(AccessLevel.NONE)
  private final String fundingTime;

  private final String displayName;
  private final CoinbaseMarginRate intradayMarginRate;
  private final CoinbaseMarginRate overnightMarginRate;
  private final BigDecimal settlementPrice;
  private final String futuresAssetType;
  private final BigDecimal indexPrice;

  /**
   * Legacy six-field constructor retained for existing response fixture owners.
   *
   * @deprecated use the full-field constructor so all exchange-provided metadata is retained
   */
  @Deprecated
  public CoinbaseFutureProductDetails(
      String contractRootUnit,
      String fundingRate,
      String fundingTime,
      CoinbaseMarginRate intradayMarginRate,
      CoinbaseMarginRate overnightMarginRate,
      CoinbasePerpetualDetails perpetualDetails) {
    this(
        null,
        null,
        null,
        null,
        contractRootUnit,
        null,
        null,
        null,
        null,
        null,
        perpetualDetails,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        fundingRate,
        fundingTime,
        null,
        intradayMarginRate,
        overnightMarginRate,
        null,
        null,
        null);
  }

  /**
   * Legacy constructor including the API-reported funding interval.
   *
   * @deprecated use the full-field constructor so all exchange-provided metadata is retained
   */
  @Deprecated
  public CoinbaseFutureProductDetails(
      String contractRootUnit,
      String fundingRate,
      String fundingTime,
      CoinbaseMarginRate intradayMarginRate,
      CoinbaseMarginRate overnightMarginRate,
      CoinbasePerpetualDetails perpetualDetails,
      String fundingInterval) {
    this(
        null,
        null,
        null,
        null,
        contractRootUnit,
        null,
        null,
        null,
        null,
        null,
        perpetualDetails,
        null,
        null,
        null,
        null,
        null,
        fundingInterval,
        null,
        fundingRate,
        fundingTime,
        null,
        intradayMarginRate,
        overnightMarginRate,
        null,
        null,
        null);
  }

  /** Deserializes all current fields from Advanced Trade's future_product_details object. */
  @JsonCreator
  public CoinbaseFutureProductDetails(
      @JsonProperty("venue") String venue,
      @JsonProperty("contract_code") String contractCode,
      @JsonProperty("contract_expiry") String contractExpiry,
      @JsonProperty("contract_size") String contractSize,
      @JsonProperty("contract_root_unit") String contractRootUnit,
      @JsonProperty("group_description") String groupDescription,
      @JsonProperty("contract_expiry_timezone") String contractExpiryTimezone,
      @JsonProperty("group_short_description") String groupShortDescription,
      @JsonProperty("risk_managed_by") String riskManagedBy,
      @JsonProperty("contract_expiry_type") String contractExpiryType,
      @JsonProperty("perpetual_details") CoinbasePerpetualDetails perpetualDetails,
      @JsonProperty("contract_display_name") String contractDisplayName,
      @JsonProperty("time_to_expiry_ms") Long timeToExpiryMs,
      @JsonProperty("non_crypto") Boolean nonCrypto,
      @JsonProperty("contract_expiry_name") String contractExpiryName,
      @JsonProperty("twenty_four_by_seven") Boolean twentyFourBySeven,
      @JsonProperty("funding_interval") String fundingInterval,
      @JsonProperty("open_interest") String openInterest,
      @JsonProperty("funding_rate") String fundingRate,
      @JsonProperty("funding_time") String fundingTime,
      @JsonProperty("display_name") String displayName,
      @JsonProperty("intraday_margin_rate") CoinbaseMarginRate intradayMarginRate,
      @JsonProperty("overnight_margin_rate") CoinbaseMarginRate overnightMarginRate,
      @JsonProperty("settlement_price") String settlementPrice,
      @JsonProperty("futures_asset_type") String futuresAssetType,
      @JsonProperty("index_price") String indexPrice) {
    this.venue = venue;
    this.contractCode = contractCode;
    this.contractExpiry = contractExpiry;
    this.contractSize = contractSize;
    this.contractRootUnit = contractRootUnit;
    this.groupDescription = groupDescription;
    this.contractExpiryTimezone = contractExpiryTimezone;
    this.groupShortDescription = groupShortDescription;
    this.riskManagedBy = riskManagedBy;
    this.contractExpiryType = contractExpiryType;
    this.perpetualDetails = perpetualDetails;
    this.contractDisplayName = contractDisplayName;
    this.timeToExpiryMs = timeToExpiryMs;
    this.nonCrypto = nonCrypto;
    this.contractExpiryName = contractExpiryName;
    this.twentyFourBySeven = twentyFourBySeven;
    this.fundingInterval = parseDuration(fundingInterval);
    this.openInterest = parseBigDecimal(openInterest);
    this.fundingRate = fundingRate;
    this.fundingTime = fundingTime;
    this.displayName = displayName;
    this.intradayMarginRate = intradayMarginRate;
    this.overnightMarginRate = overnightMarginRate;
    this.settlementPrice = parseBigDecimal(settlementPrice);
    this.futuresAssetType = futuresAssetType;
    this.indexPrice = parseBigDecimal(indexPrice);
  }

  static BigDecimal parseBigDecimal(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  static Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private static Duration parseDuration(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    try {
      if (normalized.startsWith("P") || normalized.startsWith("p")) {
        return Duration.parse(normalized.toUpperCase());
      }
      if (normalized.endsWith("s") || normalized.endsWith("S")) {
        normalized = normalized.substring(0, normalized.length() - 1);
      }
      return Duration.ofSeconds(Long.parseLong(normalized));
    } catch (DateTimeParseException | NumberFormatException ignored) {
      return null;
    }
  }

  /**
   * @return exchange venue, such as {@code FCM} or {@code INTX}.
   */
  public String getVenue() {
    return venue;
  }

  /**
   * Returns the raw contract-size wire value.
   *
   * @deprecated use {@link #getContractSizeValue()} for numeric calculations
   */
  @Deprecated
  public String getContractSize() {
    return contractSize;
  }

  /**
   * @return quantity represented by one contract, or {@code null} when absent or malformed.
   */
  public BigDecimal getContractSizeValue() {
    return parseBigDecimal(contractSize);
  }

  /**
   * @return API-reported funding interval.
   */
  public Duration getFundingInterval() {
    return fundingInterval;
  }

  /**
   * Returns the typed funding rate retained by the legacy public contract.
   *
   * @return parsed funding rate, or {@code null} when absent or malformed
   */
  public BigDecimal getFundingRate() {
    return parseBigDecimal(fundingRate);
  }

  /**
   * Returns the raw funding-rate wire value.
   *
   * @return raw wire value, or {@code null} when absent
   * @since 1.0.2
   */
  @JsonIgnore
  public String getFundingRateWireValue() {
    return fundingRate;
  }

  /**
   * @return parsed funding rate, or {@code null} when absent or malformed.
   */
  public BigDecimal getFundingRateValue() {
    return getFundingRate();
  }

  /**
   * Returns the typed funding timestamp retained by the legacy public contract.
   *
   * @return parsed funding timestamp, or {@code null} when absent or malformed
   */
  public Instant getFundingTime() {
    return parseInstant(fundingTime);
  }

  /**
   * Returns the raw funding-time wire value.
   *
   * @return raw wire value, or {@code null} when absent
   * @since 1.0.2
   */
  @JsonIgnore
  public String getFundingTimeWireValue() {
    return fundingTime;
  }

  /**
   * @return parsed funding timestamp, or {@code null} when absent or malformed.
   */
  public Instant getFundingTimeInstant() {
    return getFundingTime();
  }

  /**
   * @return intraday long/short margin rates.
   */
  public CoinbaseMarginRate getIntradayMarginRate() {
    return intradayMarginRate;
  }

  /**
   * @return overnight long/short margin rates.
   */
  public CoinbaseMarginRate getOvernightMarginRate() {
    return overnightMarginRate;
  }

  @Override
  public String toString() {
    return "CoinbaseFutureProductDetails [venue="
        + venue
        + ", contractSize="
        + contractSize
        + ", contractExpiryType="
        + contractExpiryType
        + ", fundingInterval="
        + fundingInterval
        + "]";
  }
}
