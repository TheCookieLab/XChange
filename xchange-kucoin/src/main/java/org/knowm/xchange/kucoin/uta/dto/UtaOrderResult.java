package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Result of a UTA order placement or cancellation. */
@Data
public class UtaOrderResult {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("ts")
  private Long ts;
}
