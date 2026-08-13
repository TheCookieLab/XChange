package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * One position from {@code /v5/position/list}. {@code positionIdx} carries the hedge-mode
 * subposition identity (0 one-way, 1 long, 2 short) and is preserved losslessly.
 */
@Builder
@Jacksonized
@Value
public class BybitPosition {

  @JsonProperty("positionIdx")
  String positionIdx;

  @JsonProperty("riskId")
  String riskId;

  @JsonProperty("riskLimitValue")
  String riskLimitValue;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("side")
  String side;

  @JsonProperty("size")
  String size;

  @JsonProperty("avgPrice")
  String avgPrice;

  @JsonProperty("positionValue")
  String positionValue;

  @JsonProperty("tradeMode")
  String tradeMode;

  @JsonProperty("positionStatus")
  String positionStatus;

  @JsonProperty("leverage")
  String leverage;

  @JsonProperty("markPrice")
  String markPrice;

  @JsonProperty("liqPrice")
  String liqPrice;

  @JsonProperty("trailingStop")
  String trailingStop;

  @JsonProperty("unrealisedPnl")
  String unrealisedPnl;

  @JsonProperty("cumRealisedPnl")
  String cumRealisedPnl;

  @JsonProperty("createdTime")
  String createdTime;

  @JsonProperty("updatedTime")
  String updatedTime;

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

  @JsonProperty("positionMM")
  String positionMM;

  @JsonProperty("positionIM")
  String positionIM;

  @JsonProperty("autoAddMargin")
  String autoAddMargin;

  @JsonProperty("marginMode")
  String marginMode;

  @JsonProperty("positionBalance")
  String positionBalance;

  @JsonProperty("tpLimitPrice")
  String tpLimitPrice;

  @JsonProperty("slLimitPrice")
  String slLimitPrice;

  @JsonProperty("isReduceOnly")
  Boolean isReduceOnly;

  @JsonProperty("isLeverage")
  String isLeverage;

  @JsonProperty("mmrWithAutoMM")
  String mmrWithAutoMM;
}
