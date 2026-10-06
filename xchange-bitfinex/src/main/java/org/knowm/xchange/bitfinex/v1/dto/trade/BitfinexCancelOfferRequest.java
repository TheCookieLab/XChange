package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexCancelOfferRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("offer_id")
  @JsonRawValue
  private long offerId;

  public BitfinexCancelOfferRequest(long offerId) {

    this.request = "/v1/offer/cancel";
    this.offerId = offerId;
  }

  public String getOrderId() {

    return String.valueOf(offerId);
  }
}
