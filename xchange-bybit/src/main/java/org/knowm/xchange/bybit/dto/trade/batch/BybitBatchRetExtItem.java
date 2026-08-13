package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One per-item success/error entry of a batch response's {@code retExtInfo.list}. */
@Builder
@Jacksonized
@Value
public class BybitBatchRetExtItem {

  @JsonProperty("code")
  Integer code;

  @JsonProperty("msg")
  String msg;
}
