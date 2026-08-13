package org.knowm.xchange.bybit.dto.trade.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.bybit.dto.trade.BybitOrderStatus;
import org.knowm.xchange.bybit.dto.trade.BybitOrderType;
import org.knowm.xchange.bybit.dto.trade.BybitSide;

/**
 * One closed/active order from {@code /v5/order/history}. Carries the full order record including
 * {@code orderLinkId}, rejection data, trigger/TP-SL configuration and leaves quantities with exact
 * string numerics.
 */
@Builder
@Jacksonized
@Value
public class BybitOrderHistoryDetail {

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

  @JsonProperty("qty")
  String qty;

  @JsonProperty("orderStatus")
  BybitOrderStatus orderStatus;

  @JsonProperty("cumExecQty")
  String cumExecQty;

  @JsonProperty("cumExecValue")
  String cumExecValue;

  @JsonProperty("cumExecFee")
  String cumExecFee;

  @JsonProperty("avgPrice")
  String avgPrice;

  @JsonProperty("price")
  String price;

  @JsonProperty("triggerPrice")
  String triggerPrice;

  @JsonProperty("triggerDirection")
  String triggerDirection;

  @JsonProperty("triggerBy")
  String triggerBy;

  @JsonProperty("stopOrderType")
  String stopOrderType;

  @JsonProperty("takeProfit")
  String takeProfit;

  @JsonProperty("stopLoss")
  String stopLoss;

  @JsonProperty("tpTriggerBy")
  String tpTriggerBy;

  @JsonProperty("slTriggerBy")
  String slTriggerBy;

  @JsonProperty("tpslMode")
  String tpslMode;

  @JsonProperty("tpLimitPrice")
  String tpLimitPrice;

  @JsonProperty("slLimitPrice")
  String slLimitPrice;

  @JsonProperty("reduceOnly")
  String reduceOnly;

  @JsonProperty("positionIdx")
  String positionIdx;

  @JsonProperty("timeInForce")
  String timeInForce;

  @JsonProperty("cancelType")
  String cancelType;

  @JsonProperty("rejectReason")
  String rejectReason;

  @JsonProperty("leavesQty")
  String leavesQty;

  @JsonProperty("leavesValue")
  String leavesValue;

  @JsonProperty("createdTime")
  String createdTime;

  @JsonProperty("updatedTime")
  String updatedTime;
}
