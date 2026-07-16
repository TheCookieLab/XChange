package org.knowm.xchange.coinbasederivatives.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Coinbase-managed portfolio summary for one collateral currency. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesAccountSummary(
    String id,
    String currency,
    BigDecimal balance,
    BigDecimal equity,
    @JsonProperty("available_funds") BigDecimal availableFunds,
    @JsonProperty("available_withdrawal_funds") BigDecimal availableWithdrawalFunds,
    @JsonProperty("initial_margin") BigDecimal initialMargin,
    @JsonProperty("maintenance_margin") BigDecimal maintenanceMargin,
    @JsonProperty("margin_balance") BigDecimal marginBalance,
    @JsonProperty("margin_model") String marginModel,
    @JsonProperty("futures_session_rpl") BigDecimal realizedPnl,
    @JsonProperty("futures_session_upl") BigDecimal unrealizedPnl) {}
