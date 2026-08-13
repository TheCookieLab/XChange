package org.knowm.xchange.bybit.dto.trade.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Wrapper of {@code /v5/order/history}. */
@Builder
@Jacksonized
@Value
public class BybitOrderHistoryDetails {

  @JsonProperty("category")
  String category;

  @JsonProperty("list")
  List<BybitOrderHistoryDetail> list;

  @JsonProperty("nextPageCursor")
  String nextPageCursor;
}
