package org.knowm.xchange.bitfinex.v1.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexEmptyRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("options")
  @JsonRawValue
  protected String options;

  /** Constructor */
  public BitfinexEmptyRequest(String request) {

    this.request = request;
    this.options = "[]";
  }

  public String getRequest() {

    return request;
  }

  public void setRequest(String request) {

    this.request = request;
  }

  public String getOptions() {

    return options;
  }

  public void setOptions(String options) {

    this.options = options;
  }
}
