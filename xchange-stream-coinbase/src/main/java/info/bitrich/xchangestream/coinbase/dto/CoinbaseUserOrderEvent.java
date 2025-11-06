package info.bitrich.xchangestream.coinbase.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;

public final class CoinbaseUserOrderEvent {
  private final String orderId;
  private final String clientOrderId;
  private final CurrencyPair product;
  private final Order.OrderType side;
  private final String orderType;
  private final BigDecimal limitPrice;
  private final BigDecimal averagePrice;
  private final BigDecimal size;
  private final BigDecimal filledSize;
  private final BigDecimal remainingSize;
  private final String status;
  private final Instant eventTime;

  public CoinbaseUserOrderEvent(
      String orderId,
      String clientOrderId,
      CurrencyPair product,
      Order.OrderType side,
      String orderType,
      BigDecimal limitPrice,
      BigDecimal averagePrice,
      BigDecimal size,
      BigDecimal filledSize,
      BigDecimal remainingSize,
      String status,
      Instant eventTime) {
    this.orderId = orderId;
    this.clientOrderId = clientOrderId;
    this.product = product;
    this.side = side;
    this.orderType = orderType;
    this.limitPrice = limitPrice;
    this.averagePrice = averagePrice;
    this.size = size;
    this.filledSize = filledSize;
    this.remainingSize = remainingSize;
    this.status = status;
    this.eventTime = eventTime;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getClientOrderId() {
    return clientOrderId;
  }

  public CurrencyPair getProduct() {
    return product;
  }

  public Order.OrderType getSide() {
    return side;
  }

  public String getOrderType() {
    return orderType;
  }

  public BigDecimal getLimitPrice() {
    return limitPrice;
  }

  public BigDecimal getAveragePrice() {
    return averagePrice;
  }

  public BigDecimal getSize() {
    return size;
  }

  public BigDecimal getFilledSize() {
    return filledSize;
  }

  public BigDecimal getRemainingSize() {
    return remainingSize;
  }

  public String getStatus() {
    return status;
  }

  public Instant getEventTime() {
    return eventTime;
  }

  @Override
  public String toString() {
    return "CoinbaseUserOrderEvent{"
        + "orderId='"
        + orderId
        + '\''
        + ", product="
        + product
        + ", side="
        + side
        + ", orderType='"
        + orderType
        + '\''
        + ", limitPrice="
        + limitPrice
        + ", averagePrice="
        + averagePrice
        + ", size="
        + size
        + ", filledSize="
        + filledSize
        + ", remainingSize="
        + remainingSize
        + ", status='"
        + status
        + '\''
        + ", eventTime="
        + eventTime
        + '}';
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderId, clientOrderId, product, side, orderType, eventTime);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CoinbaseUserOrderEvent)) return false;
    CoinbaseUserOrderEvent other = (CoinbaseUserOrderEvent) obj;
    return Objects.equals(orderId, other.orderId)
        && Objects.equals(clientOrderId, other.clientOrderId)
        && Objects.equals(product, other.product)
        && side == other.side
        && Objects.equals(orderType, other.orderType)
        && Objects.equals(eventTime, other.eventTime);
  }
}

