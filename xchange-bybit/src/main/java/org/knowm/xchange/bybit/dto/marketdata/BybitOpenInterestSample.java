package org.knowm.xchange.bybit.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One open-interest sample from {@code /v5/market/open-interest} {@code result.list[]}. */
@Builder
@Jacksonized
@Value
public class BybitOpenInterestSample {

  @JsonProperty("openInterest")
  String openInterest;

  @JsonProperty("singleOpenInterest")
  String singleOpenInterest;

  @JsonProperty("timestamp")
  String timestamp;
}
