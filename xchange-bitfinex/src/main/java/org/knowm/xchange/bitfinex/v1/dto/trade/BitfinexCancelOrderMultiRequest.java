package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexCancelOrderMultiRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("order_ids")
  protected long[] orderIds;

  public BitfinexCancelOrderMultiRequest(long[] orderIds) {

    this.request = "/v1/order/cancel/multi";
    this.orderIds = orderIds;
  }

  public String getRequest() {
    return request;
  }

  public void setRequest(String request) {
    this.request = request;
  }

  public long[] getOrderIds() {
    return orderIds;
  }

  public void setOrderIds(long[] orderIds) {
    this.orderIds = orderIds;
  }
}
