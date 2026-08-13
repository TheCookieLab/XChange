package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One cancel entry in {@code POST /v5/order/cancel-batch}. */
@Builder
@Jacksonized
@Value
@JsonInclude(Include.NON_EMPTY)
public class BybitBatchCancelOrderRequest {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("orderId")
  String orderId;

  @JsonProperty("orderLinkId")
  String orderLinkId;
}
