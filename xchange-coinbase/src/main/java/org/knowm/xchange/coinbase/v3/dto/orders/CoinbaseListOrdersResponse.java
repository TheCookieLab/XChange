package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseListOrdersResponse {

  private final List<CoinbaseOrderDetail> orders;
  private final String cursor;
  private final Boolean hasNext;

  public CoinbaseListOrdersResponse(List<CoinbaseOrderDetail> orders, String cursor) {
    this(orders, cursor, cursor != null && !cursor.isBlank());
  }

  @JsonCreator
  public CoinbaseListOrdersResponse(
      @JsonProperty("orders") List<CoinbaseOrderDetail> orders,
      @JsonProperty("cursor") String cursor,
      @JsonProperty("has_next") Boolean hasNext) {
    this.orders = orders == null ? null : List.copyOf(orders);
    this.cursor = cursor;
    this.hasNext = hasNext;
  }
}
