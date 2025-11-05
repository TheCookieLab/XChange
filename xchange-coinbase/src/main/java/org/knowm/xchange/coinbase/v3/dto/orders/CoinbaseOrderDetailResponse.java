package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseOrderDetailResponse {

  private final CoinbaseOrderDetail order;

  @JsonCreator
  public CoinbaseOrderDetailResponse(@JsonProperty("order") CoinbaseOrderDetail order) {
    this.order = order;
  }

  @Override
  public String toString() {
    return "CoinbaseOrderDetailResponse [order=" + (order == null ? null : order.getOrderId()) + "]";
  }
}


