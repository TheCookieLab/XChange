package org.knowm.xchange.cryptocom.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider fee-rate row from {@code private/get-fee-rate}: one entry per instrument (or {@code ALL})
 * with the tiered maker/taker schedule and the effective tier.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComFeeRate {

  @JsonProperty("instrument_name")
  private String instrumentName;

  /** Effective fee tier currently applied to the account. */
  @JsonProperty("effective_fee_tier")
  private Integer effectiveFeeTier;

  @JsonProperty("fee_type")
  private Integer feeType;

  @JsonProperty("fee_tiers")
  private List<FeeTier> feeTiers;

  /** One tier of the maker/taker fee schedule. */
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class FeeTier {

    @JsonProperty("taker_fee_rate")
    private String takerFeeRate;

    @JsonProperty("maker_fee_rate")
    private String makerFeeRate;

    @JsonProperty("taker_effective_fee_rate")
    private String takerEffectiveFeeRate;

    @JsonProperty("maker_effective_fee_rate")
    private String makerEffectiveFeeRate;

    @JsonProperty("fee_tier")
    private Integer feeTier;
  }
}