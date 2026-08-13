package org.knowm.xchange.bybit.dto.trade.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Wrapper of {@code /v5/execution/list}. */
@Builder
@Jacksonized
@Value
public class BybitExecutions {

  @JsonProperty("category")
  String category;

  @JsonProperty("list")
  List<BybitExecution> list;

  @JsonProperty("nextPageCursor")
  String nextPageCursor;
}
