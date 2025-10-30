package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Response containing perpetuals portfolio balances.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getintxbalances">Get Perpetuals Portfolio Balances</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePerpetualsBalancesResponse {

  private final CoinbasePerpetualsBalances balances;

  @JsonCreator
  public CoinbasePerpetualsBalancesResponse(@JsonProperty("balances") CoinbasePerpetualsBalances balances) {
    this.balances = balances;
  }

  @Override
  public String toString() {
    return "CoinbasePerpetualsBalancesResponse [balances=" + balances + "]";
  }

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CoinbasePerpetualsBalances {
    private final String portfolioUuid;
    private final String collateralCurrency;
    private final BigDecimal collateralValue;
    private final BigDecimal availableCollateral;
    private final BigDecimal unrealizedPnl;
    private final BigDecimal buyingPower;
    private final BigDecimal maxWithdrawableAmount;

    @JsonCreator
    public CoinbasePerpetualsBalances(
        @JsonProperty("portfolio_uuid") String portfolioUuid,
        @JsonProperty("collateral_currency") String collateralCurrency,
        @JsonProperty("collateral_value") BigDecimal collateralValue,
        @JsonProperty("available_collateral") BigDecimal availableCollateral,
        @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
        @JsonProperty("buying_power") BigDecimal buyingPower,
        @JsonProperty("max_withdrawable_amount") BigDecimal maxWithdrawableAmount) {
      this.portfolioUuid = portfolioUuid;
      this.collateralCurrency = collateralCurrency;
      this.collateralValue = collateralValue;
      this.availableCollateral = availableCollateral;
      this.unrealizedPnl = unrealizedPnl;
      this.buyingPower = buyingPower;
      this.maxWithdrawableAmount = maxWithdrawableAmount;
    }
  }
}

