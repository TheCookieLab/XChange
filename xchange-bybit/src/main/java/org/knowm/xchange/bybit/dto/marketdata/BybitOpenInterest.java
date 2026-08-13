package org.knowm.xchange.bybit.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Open interest from {@code /v5/market/open-interest}. */
@Builder
@Jacksonized
@Value
public class BybitOpenInterest {

  @JsonProperty("category")
  String category;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("openInterest")
  String openInterest;

  @JsonProperty("timestamp")
  String timestamp;
}
