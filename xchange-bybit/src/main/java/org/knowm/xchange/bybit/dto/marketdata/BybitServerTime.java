package org.knowm.xchange.bybit.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Server time from {@code /v5/market/time}. */
@Builder
@Jacksonized
@Value
public class BybitServerTime {

  @JsonProperty("timeSecond")
  String timeSecond;

  @JsonProperty("timeNano")
  String timeNano;
}
