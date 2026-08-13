package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One risk-limit tier from the public {@code /v5/market/risk-limit} endpoint. */
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

  @JsonProperty("maintenanceMargin")
  String maintenanceMargin;

  @JsonProperty("initialMargin")
  String initialMargin;

  @JsonProperty("isLowestRisk")
  String isLowestRisk;

  @JsonProperty("maxLeverage")
  String maxLeverage;

  @JsonProperty("mmDeduction")
  String mmDeduction;
}
