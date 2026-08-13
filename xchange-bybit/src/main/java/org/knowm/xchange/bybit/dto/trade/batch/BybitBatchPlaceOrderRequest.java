package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One order entry in {@code POST /v5/order/create-batch}. */
@Builder
@Jacksonized
@Value
@JsonInclude(Include.NON_EMPTY)
public class BybitBatchPlaceOrderRequest {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("side")
  String side;

  @JsonProperty("orderType")
  String orderType;

  @JsonProperty("qty")
  String qty;

  @JsonProperty("orderLinkId")
  String orderLinkId;

  @JsonProperty("price")
  String price;

  @JsonProperty("timeInForce")
  String timeInForce;

  @JsonProperty("reduceOnly")
  String reduceOnly;

  @JsonProperty("positionIdx")
  String positionIdx;

  @JsonProperty("orderIv")
  String orderIv;

  @JsonProperty("triggerPrice")
  String triggerPrice;

  @JsonProperty("triggerDirection")
  String triggerDirection;

  @JsonProperty("triggerBy")
  String triggerBy;

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
}
