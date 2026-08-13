package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One trading-stop (TP/SL) record from {@code /v5/position/trading-stop}. */
@Builder
@Jacksonized
@Value
public class BybitTradingStopInfo {

  @JsonProperty("positionIdx")
  String positionIdx;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("takeProfit")
  String takeProfit;

  @JsonProperty("stopLoss")
  String stopLoss;

  @JsonProperty("trailingStop")
  String trailingStop;

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

  @JsonProperty("createdTime")
  String createdTime;

  @JsonProperty("updatedTime")
  String updatedTime;

  /** Wrapper of the {@code list} envelope. */
  @Builder
  @Jacksonized
  @Value
  public static class BybitTradingStopInfos {
    @JsonProperty("list")
    List<BybitTradingStopInfo> list;

    @JsonProperty("nextPageCursor")
    String nextPageCursor;
  }
}
