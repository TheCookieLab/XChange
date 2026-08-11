package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Transfer result carrying the provider transfer order id and the echoed client id. */
@Data
public class UtaTransferResult {

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;
}
