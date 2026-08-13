package org.knowm.xchange.bybit.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Open-interest envelope from {@code /v5/market/open-interest}: {@code result}. */
@Builder
@Jacksonized
@Value
public class BybitOpenInterest {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("category")
  String category;

  @JsonProperty("list")
  List<BybitOpenInterestSample> list;

  @JsonProperty("nextPageCursor")
  String nextPageCursor;
}
