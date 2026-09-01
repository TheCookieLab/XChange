package org.knowm.xchange.coinbase.v3.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Fee and volume summary returned by Coinbase's transaction-summary endpoint.
 *
 * <p>When queried with {@code product_type=FUTURE}, {@link #marginRate} and the fee tier
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

  /**
   * Creates a transaction summary from current Advanced Trade fields.
   *
   * @param totalVolume aggregate trading volume
   * @param totalFees aggregate fees
   * @param feeTier current maker/taker fee tier
   * @param marginRate wrapped margin rate
   * @param advancedTradeOnlyVolume Advanced Trade volume
   * @param advancedTradeOnlyFees Advanced Trade fees
   * @param coinbaseProVolume Coinbase Pro volume
   * @param coinbaseProFees Coinbase Pro fees
   * @param totalBalance total account balance
   * @param hasCostPlusCommission whether cost-plus commission applies
   */
  @JsonCreator
  public CoinbaseTransactionSummaryResponse(
      @JsonProperty("total_volume") BigDecimal totalVolume,
      @JsonProperty("total_fees") BigDecimal totalFees,
      @JsonProperty("fee_tier") CoinbaseFeeTier feeTier,
      @JsonProperty("margin_rate") CoinbaseDecimalValue marginRate,
      @JsonProperty("advanced_trade_only_volume") BigDecimal advancedTradeOnlyVolume,
      @JsonProperty("advanced_trade_only_fees") BigDecimal advancedTradeOnlyFees,
      @JsonProperty("coinbase_pro_volume") BigDecimal coinbaseProVolume,
      @JsonProperty("coinbase_pro_fees") BigDecimal coinbaseProFees,
      @JsonProperty("total_balance") BigDecimal totalBalance,
      @JsonProperty("has_cost_plus_commission") Boolean hasCostPlusCommission) {
    this.totalVolume = totalVolume;
    this.totalFees = totalFees;
    this.feeTier = feeTier;
    this.marginRate = marginRate == null ? null : marginRate.getValue();
    this.advancedTradeOnlyVolume = advancedTradeOnlyVolume;
    this.advancedTradeOnlyFees = advancedTradeOnlyFees;
    this.coinbaseProVolume = coinbaseProVolume;
    this.coinbaseProFees = coinbaseProFees;
    this.totalBalance = totalBalance;
    this.hasCostPlusCommission = hasCostPlusCommission;
  }

  /**
   * Preserves the pre-1.0.2 transaction-summary construction contract.
   *
   * @deprecated use the current constructor to retain CFM margin and volume fields
   */
  @Deprecated
  public CoinbaseTransactionSummaryResponse(
      BigDecimal totalVolume, BigDecimal totalFees, CoinbaseFeeTier feeTier) {
    this(totalVolume, totalFees, feeTier, null, null, null, null, null, null, null);
  }

  @Override
  public String toString() {
    return "CoinbaseTransactionSummaryResponse [totalVolume=" + totalVolume + ", totalFees="
        + totalFees + ", marginRate=" + marginRate + ", feeTier=" + feeTier + "]";
  }
}
