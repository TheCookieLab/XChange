package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Body of {@code POST /v5/position/trading-stop}. */
@Builder
@Jacksonized
@Value
public class BybitTradingStopPayload {

  @JsonProperty("category")
  String category;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("positionIdx")
  int positionIdx;

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

  @JsonProperty("activePrice")
  String activePrice;
}
