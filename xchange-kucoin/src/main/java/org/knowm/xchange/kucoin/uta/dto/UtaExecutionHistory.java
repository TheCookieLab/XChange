package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** Cursor-paginated UTA execution history. */
@Data
public class UtaExecutionHistory {

  @JsonProperty("lastId")
  private Long lastId;

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("items")
  private List<UtaExecution> items;
}
