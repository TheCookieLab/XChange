package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** UTA public trade list. */
@Data
public class UtaTradeList {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("list")
  private List<UtaTrade> list;
}
