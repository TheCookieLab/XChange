package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Request payload for editing or previewing edits to an order.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseEditOrderRequest {

  private final String orderId;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal price;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal size;
  private final CoinbaseOrderConfiguration attachedOrderConfiguration;
  private final Boolean cancelAttachedOrder;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal stopPrice;

  @JsonCreator
  public CoinbaseEditOrderRequest(
      @JsonProperty("order_id") String orderId,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("size") BigDecimal size,
      @JsonProperty("attached_order_configuration") CoinbaseOrderConfiguration attachedOrderConfiguration,
      @JsonProperty("cancel_attached_order") Boolean cancelAttachedOrder,
      @JsonProperty("stop_price") BigDecimal stopPrice) {
    this.orderId = orderId;
    this.price = price;
    this.size = size;
    this.attachedOrderConfiguration = attachedOrderConfiguration;
    this.cancelAttachedOrder = cancelAttachedOrder;
    this.stopPrice = stopPrice;
  }

  @Override
  public String toString() {
    return "CoinbaseEditOrderRequest [orderId=" + orderId + ", price=" + price + ", size=" + size
        + ", attachedOrderConfiguration=" + attachedOrderConfiguration
        + ", cancelAttachedOrder=" + cancelAttachedOrder + ", stopPrice=" + stopPrice + "]";
  }
}
