package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Request payload for closing an open position.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/orders/close-position.md">Close Position</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseClosePositionRequest {

  private final String clientOrderId;
  private final String productId;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal size;

  @JsonCreator
  public CoinbaseClosePositionRequest(
      @JsonProperty("client_order_id") String clientOrderId,
      @JsonProperty("product_id") String productId,
      @JsonProperty("size") BigDecimal size) {
    this.clientOrderId = clientOrderId;
    this.productId = productId;
    this.size = size;
  }

  @Override
  public String toString() {
    return "CoinbaseClosePositionRequest [clientOrderId=" + clientOrderId
        + ", productId=" + productId + ", size=" + size + "]";
  }
}
