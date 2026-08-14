package org.knowm.xchange.mexc.v3.dto.trade;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MEXC Spot v3 order as returned by order query and history endpoints.
 *
 * <p>The provider documents a literal {@code Qty} field on {@code GET /api/v3/order} and {@code
 * origQty} on the other order endpoints; both names are captured into the same quantity property so
 * a single DTO serves every endpoint. Any additional provider fields are preserved raw.
 */
public class MexcV3Order {

  private String symbol;
  private String orderId;
  private long orderListId;
  private String clientOrderId;
  private String price;
  private String origQty;
  private String executedQty;
  private String cummulativeQuoteQty;
  private MexcV3OrderStatus status;
  private String timeInForce;
  private MexcV3OrderType type;
  private MexcV3OrderSide side;
  private String stopPrice;
  private String icebergQty;
  private long time;
  private long updateTime;
  private boolean isWorking;
  private String origQuoteOrderQty;
  private final Map<String, Object> extra = new LinkedHashMap<>();

  public MexcV3Order(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("orderId") String orderId,
      @JsonProperty("orderListId") long orderListId,
      @JsonProperty("clientOrderId") String clientOrderId,
      @JsonProperty("price") String price,
      @JsonProperty("origQty") String origQty,
      @JsonProperty("executedQty") String executedQty,
      @JsonProperty("cummulativeQuoteQty") String cummulativeQuoteQty,
      @JsonProperty("status") MexcV3OrderStatus status,
      @JsonProperty("timeInForce") String timeInForce,
      @JsonProperty("type") MexcV3OrderType type,
      @JsonProperty("side") MexcV3OrderSide side,
      @JsonProperty("stopPrice") String stopPrice,
      @JsonProperty("icebergQty") String icebergQty,
      @JsonProperty("time") long time,
      @JsonProperty("updateTime") long updateTime,
      @JsonProperty("isWorking") boolean isWorking,
      @JsonProperty("origQuoteOrderQty") String origQuoteOrderQty) {
    this.symbol = symbol;
    this.orderId = orderId;
    this.orderListId = orderListId;
    this.clientOrderId = clientOrderId;
    this.price = price;
    this.origQty = origQty;
    this.executedQty = executedQty;
    this.cummulativeQuoteQty = cummulativeQuoteQty;
    this.status = status;
    this.timeInForce = timeInForce;
    this.type = type;
    this.side = side;
    this.stopPrice = stopPrice;
    this.icebergQty = icebergQty;
    this.time = time;
    this.updateTime = updateTime;
    this.isWorking = isWorking;
    this.origQuoteOrderQty = origQuoteOrderQty;
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

  public String getClientOrderId() {
    return clientOrderId;
  }

  public String getPrice() {
    return price;
  }

  public String getOrigQty() {
    return origQty;
  }

  /** Captures the documented literal {@code Qty} field of {@code GET /api/v3/order}. */
  @JsonProperty("Qty")
  public void setQty(String qty) {
    this.origQty = qty;
  }

  public String getExecutedQty() {
    return executedQty;
  }

  public String getCummulativeQuoteQty() {
    return cummulativeQuoteQty;
  }

  public MexcV3OrderStatus getStatus() {
    return status;
  }

  /** Time-in-force label as transmitted (e.g. {@code GTC}); not an input parameter. */
  public String getTimeInForce() {
    return timeInForce;
  }

  public MexcV3OrderType getType() {
    return type;
  }

  public MexcV3OrderSide getSide() {
    return side;
  }

  public String getStopPrice() {
    return stopPrice;
  }

  public String getIcebergQty() {
    return icebergQty;
  }

  public long getTime() {
    return time;
  }

  public long getUpdateTime() {
    return updateTime;
  }

  public boolean isWorking() {
    return isWorking;
  }

  public String getOrigQuoteOrderQty() {
    return origQuoteOrderQty;
  }

  /** Fields not explicitly modeled, preserved verbatim. */
  public Map<String, Object> getExtra() {
    return extra;
  }

  @JsonAnySetter
  public void setExtra(String name, Object value) {
    extra.put(name, value);
  }
}
