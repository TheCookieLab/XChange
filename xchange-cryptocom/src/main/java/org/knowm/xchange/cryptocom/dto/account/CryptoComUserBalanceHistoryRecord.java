package org.knowm.xchange.cryptocom.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of {@code private/user-balance-history} (wallet/history trail). Amounts and balances are
 * exact decimal strings; {@code eventType} carries the provider event category (e.g. TRADE,
 * TRANSFER).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComUserBalanceHistoryRecord {

  @JsonProperty("account_id")
  private String accountId;

  @JsonProperty("event_type")
  private String eventType;

  @JsonProperty("instrument_name")
  private String instrumentName;

  @JsonProperty("amount")
  private String amount;

  @JsonProperty("balance")
  private String balance;

  @JsonProperty("transaction_time")
  private Long transactionTime;
}