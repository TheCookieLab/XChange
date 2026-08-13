package org.knowm.xchange.bybit.dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Body of {@code POST /v5/order/pre-check} and its result. */
@Builder
@Jacksonized
@Value
@JsonInclude(Include.NON_EMPTY)
public class BybitPreCheckPayload {

  @JsonProperty("category")
  String category;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("side")
  String side;

  @JsonProperty("orderType")
  String orderType;

  @JsonProperty("qty")
  String qty;

  @JsonProperty("price")
  String price;

  @JsonProperty("triggerPrice")
  String triggerPrice;

  @JsonProperty("triggerDirection")
  String triggerDirection;

  @JsonProperty("orderIv")
  String orderIv;

  @JsonProperty("timeInForce")
  String timeInForce;

  @JsonProperty("positionIdx")
  String positionIdx;
}
