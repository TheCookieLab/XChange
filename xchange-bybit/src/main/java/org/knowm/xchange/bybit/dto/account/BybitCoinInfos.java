package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Wrapper of {@code /v5/asset/coin/query-info}. */
@Builder
@Jacksonized
@Value
public class BybitCoinInfos {

  @JsonProperty("rows")
  List<BybitCoinInfo> rows;
}
