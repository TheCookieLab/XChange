package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexNewOrderMultiRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("orders")
  protected BitfinexNewOrder[] orders;

  public BitfinexNewOrderMultiRequest(BitfinexNewOrder[] orders) {

    this.request = "/v1/order/new/multi";
    this.orders = orders;
  }

  public String getRequest() {

    return request;
  }

  public void setRequest(String request) {

    this.request = request;
  }

  public BitfinexNewOrder[] getOrders() {

    return orders;
  }

  public void setOrders(BitfinexNewOrder[] orders) {

    this.orders = orders;
  }
}
