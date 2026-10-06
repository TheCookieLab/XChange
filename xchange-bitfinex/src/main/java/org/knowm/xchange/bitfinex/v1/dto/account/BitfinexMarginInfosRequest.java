package org.knowm.xchange.bitfinex.v1.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexMarginInfosRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  public BitfinexMarginInfosRequest() {

    this.request = "/v1/margin_infos";
  }

  public String getRequest() {

    return request;
  }

  public void setRequest(String request) {

    this.request = request;
  }
}
