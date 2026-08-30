package org.knowm.xchange.coinbase.v3.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Fee and volume summary returned by Coinbase's transaction-summary endpoint.
 *
 * <p>When queried with {@code product_type=FUTURE}, {@link #getMarginRate()} and the fee tier
 * describe the current CFM economics for that response filter. Values are observations and are not
 * cached or converted by this transport DTO.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseTransactionSummaryResponse {

  private final BigDecimal totalVolume;
  private final BigDecimal totalFees;
  private final CoinbaseFeeTier feeTier;
  private final BigDecimal marginRate;
  private final BigDecimal advancedTradeOnlyVolume;
  private final BigDecimal advancedTradeOnlyFees;
  private final BigDecimal coinbaseProVolume;
  private final BigDecimal coinbaseProFees;
  private final BigDecimal totalBalance;
  private final Boolean hasCostPlusCommission;

  @JsonCreator
  public CoinbaseTransactionSummaryResponse(
      @JsonProperty("total_volume") BigDecimal totalVolume,
      @JsonProperty("total_fees") BigDecimal totalFees,
      @JsonProperty("fee_tier") CoinbaseFeeTier feeTier,
      @JsonProperty("margin_rate") BigDecimal marginRate,
      @JsonProperty("advanced_trade_only_volume") BigDecimal advancedTradeOnlyVolume,
      @JsonProperty("advanced_trade_only_fees") BigDecimal advancedTradeOnlyFees,
      @JsonProperty("coinbase_pro_volume") BigDecimal coinbaseProVolume,
      @JsonProperty("coinbase_pro_fees") BigDecimal coinbaseProFees,
      @JsonProperty("total_balance") BigDecimal totalBalance,
      @JsonProperty("has_cost_plus_commission") Boolean hasCostPlusCommission) {
    this.totalVolume = totalVolume;
    this.totalFees = totalFees;
    this.feeTier = feeTier;
    this.marginRate = marginRate;
    this.advancedTradeOnlyVolume = advancedTradeOnlyVolume;
    this.advancedTradeOnlyFees = advancedTradeOnlyFees;
    this.coinbaseProVolume = coinbaseProVolume;
    this.coinbaseProFees = coinbaseProFees;
    this.totalBalance = totalBalance;
    this.hasCostPlusCommission = hasCostPlusCommission;
  }

  @Override
  public String toString() {
    return "CoinbaseTransactionSummaryResponse [totalVolume=" + totalVolume + ", totalFees="
        + totalFees + ", marginRate=" + marginRate + ", feeTier=" + feeTier + "]";
  }
}
