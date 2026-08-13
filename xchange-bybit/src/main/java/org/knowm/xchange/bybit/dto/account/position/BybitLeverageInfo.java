package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One leverage record from {@code /v5/position/limit}. */
@Builder
@Jacksonized
@Value
public class BybitLeverageInfo {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("leverage")
  String leverage;

  @JsonProperty("maxLeverage")
  String maxLeverage;

  @JsonProperty("positionIdx")
  String positionIdx;

  @JsonProperty("riskLimitValue")
  String riskLimitValue;

}
