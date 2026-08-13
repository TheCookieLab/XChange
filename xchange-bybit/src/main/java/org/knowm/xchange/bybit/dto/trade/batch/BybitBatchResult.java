package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Full batch create/amend/cancel response. The envelope mirrors {@code BybitResult} but keeps the
 * per-item error surface ({@code retExtInfo.list}, index-aligned with {@code result.list}) typed,
 * because an overall {@code retCode 0} does not imply every item succeeded.
 */
@Builder
@Jacksonized
@Value
public class BybitBatchResult {

  @JsonProperty("retCode")
  int retCode;

  @JsonProperty("retMsg")
  String retMsg;

  @JsonProperty("result")
  BybitBatchOrderResults result;

  @JsonProperty("retExtInfo")
  BybitBatchRetExtInfo retExtInfo;

  @JsonProperty("time")
  Date time;

  public boolean isSuccess() {
    return retCode == 0;
  }
}
