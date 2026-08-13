package org.knowm.xchange.bybit.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * One public trade from {@code /v5/market/public-trades}. Numeric fields stay on the wire as
 * strings for exactness; consumers convert at the adapter boundary.
 */
@Builder
@Jacksonized
@Value
public class BybitPublicTrade {

  @JsonProperty("execId")
  String execId;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("price")
  String price;

  @JsonProperty("size")
  String size;

  @JsonProperty("side")
  String side;

  @JsonProperty("time")
  String time;

  @JsonProperty("isBlockTrade")
  Boolean isBlockTrade;
}
