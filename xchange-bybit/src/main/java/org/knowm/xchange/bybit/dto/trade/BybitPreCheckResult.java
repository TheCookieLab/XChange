package org.knowm.xchange.bybit.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Result of {@code POST /v5/order/pre-check}. */
@Builder
@Jacksonized
@Value
public class BybitPreCheckResult {

  @JsonProperty("isValid")
  Boolean isValid;

  @JsonProperty("message")
  String message;
}
