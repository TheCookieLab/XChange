package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexActiveCreditsRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  public BitfinexActiveCreditsRequest() {

    this.request = "/v1/credits";
  }
}
