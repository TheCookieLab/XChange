package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Wrapper of {@code /v5/position/limit}. */
@Builder
@Jacksonized
@Value
public class BybitLeverageInfos {

  @JsonProperty("list")
  List<BybitLeverageInfo> list;

  @JsonProperty("nextPageCursor")
  String nextPageCursor;
}
