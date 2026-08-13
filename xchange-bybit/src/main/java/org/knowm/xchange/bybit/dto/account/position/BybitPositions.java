package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Wrapper of {@code /v5/position/list}. */
@Builder
@Jacksonized
@Value
public class BybitPositions {

  @JsonProperty("category")
  String category;

  @JsonProperty("list")
  List<BybitPosition> list;

  @JsonProperty("nextPageCursor")
  String nextPageCursor;
}
