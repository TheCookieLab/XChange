package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStreamingUserOrder {

  private final String orderId;
  private final String clientOrderId;
  private final String productId;
  private final String orderSide;
  private final String orderType;
  private final BigDecimal limitPrice;
  private final BigDecimal avgPrice;
  private final BigDecimal size;
  private final BigDecimal orderTotal;
  private final BigDecimal cumulativeQuantity;
  private final BigDecimal leavesQuantity;
  private final String status;
  private final String eventTime;

  @JsonCreator
  public CoinbaseStreamingUserOrder(
      @JsonProperty("order_id") String orderId,
      @JsonProperty("client_order_id") String clientOrderId,
      @JsonProperty("product_id") String productId,
      @JsonProperty("order_side") String orderSide,
      @JsonProperty("order_type") String orderType,
      @JsonProperty("limit_price") BigDecimal limitPrice,
      @JsonProperty("avg_price") BigDecimal avgPrice,
      @JsonProperty("size") BigDecimal size,
      @JsonProperty("order_total") BigDecimal orderTotal,
      @JsonProperty("cumulative_quantity") BigDecimal cumulativeQuantity,
      @JsonProperty("leaves_quantity") BigDecimal leavesQuantity,
      @JsonProperty("status") String status,
      @JsonProperty("event_time") String eventTime) {
    this.orderId = orderId;
    this.clientOrderId = clientOrderId;
    this.productId = productId;
    this.orderSide = orderSide;
    this.orderType = orderType;
    this.limitPrice = limitPrice;
    this.avgPrice = avgPrice;
    this.size = size;
    this.orderTotal = orderTotal;
    this.cumulativeQuantity = cumulativeQuantity;
    this.leavesQuantity = leavesQuantity;
    this.status = status;
    this.eventTime = eventTime;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getClientOrderId() {
    return clientOrderId;
  }

  public String getProductId() {
    return productId;
  }

  public String getOrderSide() {
    return orderSide;
  }

  public String getOrderType() {
    return orderType;
  }

  public BigDecimal getLimitPrice() {
    return limitPrice;
  }

  public BigDecimal getAvgPrice() {
    return avgPrice;
  }

  public BigDecimal getSize() {
    return size;
  }

  public BigDecimal getOrderTotal() {
    return orderTotal;
  }

  public BigDecimal getCumulativeQuantity() {
    return cumulativeQuantity;
  }

  public BigDecimal getLeavesQuantity() {
    return leavesQuantity;
  }

  public String getStatus() {
    return status;
  }

  public String getEventTime() {
    return eventTime;
  }
}
