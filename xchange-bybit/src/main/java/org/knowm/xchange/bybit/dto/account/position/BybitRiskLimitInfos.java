package org.knowm.xchange.bybit.dto.account.position;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Wrapper of {@code /v5/position/risk-limit}. */
@Builder
@Jacksonized
@Value
public class BybitRiskLimitInfos {

  @JsonProperty("list")
  List<BybitRiskLimitInfo> list;

  @JsonProperty("nextPageCursor")
  String nextPageCursor;
}
