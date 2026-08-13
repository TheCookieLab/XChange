package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Body of {@code POST /v5/position/add-margin}. */
@Builder
@Jacksonized
@Value
public class BybitAddMarginPayload {

  @JsonProperty("category")
  String category;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("margin")
  String margin;

  @JsonProperty("positionIdx")
  String positionIdx;
}
