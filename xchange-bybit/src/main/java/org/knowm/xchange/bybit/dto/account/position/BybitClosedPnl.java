package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One closed PnL record from {@code /v5/position/closed-pnl}. */
@Builder
@Jacksonized
@Value
public class BybitClosedPnl {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("orderId")
  String orderId;

  @JsonProperty("orderLinkId")
  String orderLinkId;

  @JsonProperty("closedPnl")
  String closedPnl;

  @JsonProperty("avgEntryPrice")
  String avgEntryPrice;

  @JsonProperty("qty")
  String qty;

  @JsonProperty("closedSize")
  String closedSize;

  @JsonProperty("createdTime")
  String createdTime;

  @JsonProperty("updatedTime")
  String updatedTime;
}
