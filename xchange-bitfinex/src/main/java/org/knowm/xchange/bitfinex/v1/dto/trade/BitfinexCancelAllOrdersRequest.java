package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexCancelAllOrdersRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  /** Constructor */
  public BitfinexCancelAllOrdersRequest() {

    this.request = "/v1/order/cancel/all";
  }
}
