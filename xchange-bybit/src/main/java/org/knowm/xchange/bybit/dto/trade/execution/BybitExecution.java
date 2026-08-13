package org.knowm.xchange.bybit.dto.trade.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.bybit.dto.trade.BybitOrderType;
import org.knowm.xchange.bybit.dto.trade.BybitSide;

/** One fill/execution from {@code /v5/execution/list}. */
@Builder
@Jacksonized
@Value
public class BybitExecution {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("orderId")
  String orderId;

  @JsonProperty("orderLinkId")
  String orderLinkId;

  @JsonProperty("side")
  BybitSide side;

  @JsonProperty("orderType")
  BybitOrderType orderType;

  @JsonProperty("execType")
  String execType;

  @JsonProperty("execFee")
  String execFee;

  @JsonProperty("execPrice")
  String execPrice;

  @JsonProperty("execQty")
  String execQty;

  @JsonProperty("execTime")
  String execTime;

  @JsonProperty("feeRate")
  String feeRate;

  @JsonProperty("closedPnl")
  String closedPnl;

  @JsonProperty("isMaker")
  Boolean isMaker;

  @JsonProperty("seq")
  String seq;

  @JsonProperty("blockTradeId")
  String blockTradeId;

  @JsonProperty("createdAt")
  String createdAt;

  @JsonProperty("updatedTime")
  String updatedTime;
}
