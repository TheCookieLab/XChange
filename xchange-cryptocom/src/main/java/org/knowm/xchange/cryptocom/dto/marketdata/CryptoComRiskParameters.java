package org.knowm.xchange.cryptocom.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Derivative risk reference data from {@code public/get-risk-parameters} (official "Smart Cross
 * Margin" risk parameters). All numerics are exact decimal strings; values of {@code "-1"}
 * indicate "no limit applies". Typed raw reference data — no XChange core surface.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComRiskParameters {

  @JsonProperty("default_max_product_leverage_for_spot")
  private String defaultMaxProductLeverageForSpot;

  @JsonProperty("default_max_product_leverage_for_perps")
  private String defaultMaxProductLeverageForPerps;

  @JsonProperty("default_max_product_leverage_for_futures")
  private String defaultMaxProductLeverageForFutures;

  @JsonProperty("default_umr_multiplier_for_spot")
  private String defaultUmrMultiplierForSpot;

  @JsonProperty("default_umr_multiplier_for_perps")
  private String defaultUmrMultiplierForPerps;

  @JsonProperty("default_umr_multiplier_for_futures")
  private String defaultUmrMultiplierForFutures;

  @JsonProperty("default_long_pos_limit_perps")
  private String defaultLongPosLimitPerps;

  @JsonProperty("default_short_pos_limit_perps")
  private String defaultShortPosLimitPerps;

  @JsonProperty("default_long_pos_limit_futures")
  private String defaultLongPosLimitFutures;

  @JsonProperty("default_short_pos_limit_futures")
  private String defaultShortPosLimitFutures;

  @JsonProperty("default_unit_margin_rate")
  private String defaultUnitMarginRate;

  @JsonProperty("default_collateral_cap")
  private String defaultCollateralCap;

  @JsonProperty("update_timestamp_ms")
  private String updateTimestampMs;

  @JsonProperty("base_currency_config")
  private List<BaseCurrencyConfig> baseCurrencyConfig;

  /** Per-base-currency risk configuration. */
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BaseCurrencyConfig {

    @JsonProperty("instrument_name")
    private String instrumentName;

    @JsonProperty("collateral_cap_notional")
    private String collateralCapNotional;

    @JsonProperty("minimum_haircut")
    private String minimumHaircut;

    @JsonProperty("max_product_leverage_for_spot")
    private String maxProductLeverageForSpot;

    @JsonProperty("max_product_leverage_for_perps")
    private String maxProductLeverageForPerps;

    @JsonProperty("max_product_leverage_for_futures")
    private String maxProductLeverageForFutures;

    @JsonProperty("unit_margin_rate")
    private String unitMarginRate;

    @JsonProperty("umr_multiplier_for_spot")
    private String umrMultiplierForSpot;

    @JsonProperty("umr_multiplier_for_perps")
    private String umrMultiplierForPerps;

    @JsonProperty("umr_multiplier_for_futures")
    private String umrMultiplierForFutures;

    @JsonProperty("long_pos_limit_perps")
    private String longPosLimitPerps;

    @JsonProperty("short_pos_limit_perps")
    private String shortPosLimitPerps;

    @JsonProperty("long_pos_limit_futures")
    private String longPosLimitFutures;

    @JsonProperty("short_pos_limit_futures")
    private String shortPosLimitFutures;

    @JsonProperty("collateral_weight")
    private String collateralWeight;

    @JsonProperty("haircut_bucket")
    private String haircutBucket;
  }
}