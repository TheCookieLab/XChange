package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** UTA ticker snapshot for one or all symbols. */
@Data
public class UtaTickerList {

  @JsonProperty("tradeType")
  private String tradeType;

  /** Timestamp in nanoseconds. */
  @JsonProperty("ts")
  private Long ts;

  @JsonProperty("list")
  private List<UtaTicker> list;
}
