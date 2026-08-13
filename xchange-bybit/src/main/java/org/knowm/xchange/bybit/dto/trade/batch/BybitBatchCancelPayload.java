package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Body of {@code POST /v5/order/cancel-batch}. */
@Builder
@Jacksonized
@Value
@JsonInclude(Include.NON_EMPTY)
public class BybitBatchCancelPayload {

  @JsonProperty("category")
  String category;

  @JsonProperty("request")
  List<BybitBatchCancelOrderRequest> request;
}
