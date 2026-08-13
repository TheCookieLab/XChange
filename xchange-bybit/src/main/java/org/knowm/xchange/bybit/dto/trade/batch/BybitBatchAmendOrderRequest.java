package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One amend entry in {@code POST /v5/order/amend-batch}. */
@Builder
@Jacksonized
@Value
@JsonInclude(Include.NON_EMPTY)
public class BybitBatchAmendOrderRequest {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("orderId")
  String orderId;

  @JsonProperty("orderLinkId")
  String orderLinkId;

  @JsonProperty("orderIv")
  String orderIv;

  @JsonProperty("triggerPrice")
  String triggerPrice;

  @JsonProperty("qty")
  String qty;

  @JsonProperty("price")
  String price;

  @JsonProperty("tpslMode")
  String tpslMode;

  @JsonProperty("takeProfit")
  String takeProfit;

  @JsonProperty("stopLoss")
  String stopLoss;

  @JsonProperty("tpTriggerBy")
  String tpTriggerBy;

  @JsonProperty("slTriggerBy")
  String slTriggerBy;

  @JsonProperty("triggerBy")
  String triggerBy;

  @JsonProperty("tpLimitPrice")
  String tpLimitPrice;

  @JsonProperty("slLimitPrice")
  String slLimitPrice;
}
