package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * One item of a batch create response. Only {@code create-batch} returns {@code createAt}; cancel
 * and amend items carry {@code category}/{@code symbol}/{@code orderId}/{@code orderLinkId} only.
 * Per-item failures do not appear here — they are reported index-aligned in the response's {@code
 * retExtInfo.list} (see {@link BybitBatchResult}).
 */
@Builder
@Jacksonized
@Value
@JsonInclude(Include.NON_EMPTY)
public class BybitBatchOrderResult {

  @JsonProperty("category")
  String category;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("orderId")
  String orderId;

  @JsonProperty("orderLinkId")
  String orderLinkId;

  @JsonProperty("createAt")
  String createAt;
}
