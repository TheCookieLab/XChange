package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Body of {@code POST /v5/position/set-risk-limit}. */
@Builder
@Jacksonized
@Value
public class BybitSetRiskLimitPayload {

  @JsonProperty("category")
  String category;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("riskId")
  String riskId;

  @JsonProperty("positionIdx")
  String positionIdx;
}
