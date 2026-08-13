package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One transaction record from {@code /v5/account/transaction-log}, bound to the documented wire keys. */
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

  /** Transaction timestamp (ms). */
  @JsonProperty("transactionTime")
  String transactionTime;

  @JsonProperty("type")
  String type;

  /** Transaction sub type; {@code movePosition} for move-position logs, otherwise empty. */
  @JsonProperty("transSubType")
  String transSubType;

  /** Quantity. For spot the sign carries direction; perps & futures have no direction. */
  @JsonProperty("qty")
  String qty;

  /** Remaining position size after the trade; carries direction (short with "-"). */
  @JsonProperty("size")
  String size;

  @JsonProperty("currency")
  String currency;

  @JsonProperty("tradePrice")
  String tradePrice;

  @JsonProperty("funding")
  String funding;

  @JsonProperty("fee")
  String fee;

  @JsonProperty("cashFlow")
  String cashFlow;

  @JsonProperty("change")
  String change;

  @JsonProperty("cashBalance")
  String cashBalance;

  @JsonProperty("feeRate")
  String feeRate;

  @JsonProperty("bonusChange")
  String bonusChange;

  @JsonProperty("tradeId")
  String tradeId;

  @JsonProperty("orderId")
  String orderId;

  @JsonProperty("orderLinkId")
  String orderLinkId;

  @JsonProperty("extraFees")
  String extraFees;
}
