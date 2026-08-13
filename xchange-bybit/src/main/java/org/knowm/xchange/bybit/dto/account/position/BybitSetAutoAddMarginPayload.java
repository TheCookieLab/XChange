package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Body of {@code POST /v5/position/set-auto-add-margin}. */
@Builder
@Jacksonized
@Value
public class BybitSetAutoAddMarginPayload {

  @JsonProperty("category")
  String category;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("autoAddMargin")
  String autoAddMargin;

  @JsonProperty("positionIdx")
  String positionIdx;
}
