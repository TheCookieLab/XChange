package org.knowm.xchange.mexc.v3.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Placement acknowledgment from {@code POST /api/v3/order}.
 *
 * <p>The provider returns a fixed short shape (no ACK/RESULT/FULL variants); {@code orderId} is a
 * string.
 */
public class MexcV3OrderResponse {

  private final String symbol;
  private final String orderId;
  private final long orderListId;
  private final String price;
  private final String origQty;
  private final MexcV3OrderType type;
  private final MexcV3OrderSide side;
  private final long transactTime;

  public MexcV3OrderResponse(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("orderId") String orderId,
      @JsonProperty("orderListId") long orderListId,
      @JsonProperty("price") String price,
      @JsonProperty("origQty") String origQty,
      @JsonProperty("type") MexcV3OrderType type,
      @JsonProperty("side") MexcV3OrderSide side,
      @JsonProperty("transactTime") long transactTime) {
    this.symbol = symbol;
    this.orderId = orderId;
    this.orderListId = orderListId;
    this.price = price;
    this.origQty = origQty;
    this.type = type;
    this.side = side;
    this.transactTime = transactTime;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getOrderId() {
    return orderId;
  }

  public long getOrderListId() {
    return orderListId;
  }

  public String getPrice() {
    return price;
  }

  public String getOrigQty() {
    return origQty;
  }

  public MexcV3OrderType getType() {
    return type;
  }

  public MexcV3OrderSide getSide() {
    return side;
  }

  public long getTransactTime() {
    return transactTime;
  }
}
