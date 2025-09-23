package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseListOrdersResponse {

  private final List<CoinbaseOrderDetail> orders;
  private final String cursor;

  @JsonCreator
  public CoinbaseListOrdersResponse(
      @JsonProperty("orders") List<CoinbaseOrderDetail> orders,
      @JsonProperty("cursor") String cursor) {
    this.orders = orders == null ? Collections.emptyList() : Collections.unmodifiableList(orders);
    this.cursor = cursor;
  }
}


