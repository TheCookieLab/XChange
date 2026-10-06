package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexNonceOnlyRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  /**
   * Constructor
   *
   * @param request
   */
  public BitfinexNonceOnlyRequest(String request) {

    this.request = request;
  }
}
