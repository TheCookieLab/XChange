package org.knowm.xchange.cryptocom.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComBalance {

  @JsonProperty("instrument_name")
  private String instrumentName;

  @JsonProperty("total_available_balance")
  private String totalAvailableBalance;

  @JsonProperty("total_margin_balance")
  private String totalMarginBalance;

  @JsonProperty("total_cash_balance")
  private String totalCashBalance;

  /** Total equity/cash + unrealised PnL view exposed by the account summary. */
  @JsonProperty("total_effective_balance")
  private String totalEffectiveBalance;

  /** Aggregate initial margin across all derivative positions (margin/collateral). */
  @JsonProperty("total_initial_margin")
  private String totalInitialMargin;

  /** Aggregate maintenance margin across all derivative positions (margin/collateral). */
  @JsonProperty("total_maintenance_margin")
  private String totalMaintenanceMargin;

  /** Margin locked by open positions (margin/collateral). */
  @JsonProperty("total_position_margin")
  private String totalPositionMargin;

  /** Total collateral posted against current liabilities. */
  @JsonProperty("total_collateral")
  private String totalCollateral;

  @JsonProperty("position_balances")
  private List<PositionBalance> positionBalances;

  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PositionBalance {

    @JsonProperty("instrument_name")
    private String instrumentName;

    @JsonProperty("quantity")
    private String quantity;

    @JsonProperty("market_value")
    private String marketValue;

    @JsonProperty("reserved_qty")
    private String reservedQty;
  }
}
