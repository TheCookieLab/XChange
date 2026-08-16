package org.knowm.xchange.cryptocom.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Account/risk summary row from {@code private/get-accounts}. Exposes the provider account type,
 * character type and margin risk model used as the account-level risk summary.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComAccount {

  @JsonProperty("account_id")
  private String accountId;

  @JsonProperty("account_type")
  private String accountType;

  @JsonProperty("main_account_type")
  private String mainAccountType;

  @JsonProperty("character_type")
  private String characterType;

  /** Margin risk model governing the account (e.g. PORTFOLIO_MARGIN / SB_RSM). */
  @JsonProperty("margin_risk_model")
  private String marginRiskModel;

  @JsonProperty("currency")
  private String currency;
}