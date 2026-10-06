package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexOrdersHistoryRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("limit")
  @JsonRawValue
  private long limit;

  /**
   * Constructor
   *
   * @param limit
   */
  public BitfinexOrdersHistoryRequest(long limit) {

    this.request = "/v1/orders/hist";
    this.limit = limit;
  }
}
