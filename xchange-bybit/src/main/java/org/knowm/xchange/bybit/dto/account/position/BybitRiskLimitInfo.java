package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One risk-limit tier from {@code /v5/position/risk-limit}. */
@Builder
@Jacksonized
@Value
public class BybitRiskLimitInfo {

  @JsonProperty("id")
  String id;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("riskLimitValue")
  String riskLimitValue;

  @JsonProperty("maintainMargin")
  String maintainMargin;

  @JsonProperty("initialMargin")
  String initialMargin;

  @JsonProperty("isLowestRisk")
  String isLowestRisk;

  @JsonProperty("section")
  List<String> section;

  @JsonProperty("maxLeverage")
  String maxLeverage;

  @JsonProperty("positionIdx")
  String positionIdx;

  @JsonProperty("marginMode")
  String marginMode;

  /** Wrapper of the {@code list} envelope. */
  @Builder
  @Jacksonized
  @Value
  public static class BybitRiskLimitInfos {
    @JsonProperty("list")
    List<BybitRiskLimitInfo> list;

    @JsonProperty("nextPageCursor")
    String nextPageCursor;
  }
}
