package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Private WebSocket token response from {@code POST /api/v2/bullet-private}. */
@Data
public class UtaWsToken {

  @JsonProperty("token")
  private String token;
}
