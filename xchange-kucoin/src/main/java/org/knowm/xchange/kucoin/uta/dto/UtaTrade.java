package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/** UTA public trade. */
@Data
public class UtaTrade {

  @JsonProperty("sequence")
  private Long sequence;

  @JsonProperty("tradeId")
  private String tradeId;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("size")
  private BigDecimal size;

  @JsonProperty("side")
  private String side;

  /** Timestamp in nanoseconds. */
  @JsonProperty("ts")
  private Long ts;

  @JsonProperty("isRpiTrade")
  private Boolean isRpiTrade;
}
