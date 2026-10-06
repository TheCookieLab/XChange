package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexOrderStatusRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("order_id")
  @JsonRawValue
  private long orderId;

  /**
   * Constructor
   *
   * @param orderId
   */
  public BitfinexOrderStatusRequest(long orderId) {

    this.request = "/v1/order/status";
    this.orderId = orderId;
  }

  public String getOrderId() {

    return String.valueOf(orderId);
  }
}
