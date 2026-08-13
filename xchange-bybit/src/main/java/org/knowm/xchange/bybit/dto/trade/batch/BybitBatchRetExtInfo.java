package org.knowm.xchange.bybit.dto.trade.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * The {@code retExtInfo} object of a batch response: per-item success/error entries, index-aligned
 * with {@code result.list}.
 */
@Builder
@Jacksonized
@Value
public class BybitBatchRetExtInfo {

  @JsonProperty("list")
  List<BybitBatchRetExtItem> list;
}
