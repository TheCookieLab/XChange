package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One transaction record from {@code /v5/account/transaction-log}. */
@Builder
@Jacksonized
@Value
public class BybitTransactionLog {

  @JsonProperty("id")
  String id;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("category")
  String category;

  @JsonProperty("side")
  String side;

  @JsonProperty("type")
  String type;

  @JsonProperty("amount")
  String amount;

  @JsonProperty("fee")
  String fee;

  @JsonProperty("cashFlow")
  String cashFlow;

  @JsonProperty("change")
  String change;

  @JsonProperty("cashBalance")
  String cashBalance;

  @JsonProperty("currency")
  String currency;

  @JsonProperty("execPrice")
  String execPrice;

  @JsonProperty("executionId")
  String executionId;

  @JsonProperty("tradeTime")
  String tradeTime;

  @JsonProperty("subType")
  String subType;

  @JsonProperty("feeRate")
  String feeRate;

  @JsonProperty("closedPnl")
  String closedPnl;

  @JsonProperty("bonusChange")
  String bonusChange;

  @JsonProperty("leverage")
  String leverage;

  @JsonProperty("tradeIv")
  String tradeIv;
}
