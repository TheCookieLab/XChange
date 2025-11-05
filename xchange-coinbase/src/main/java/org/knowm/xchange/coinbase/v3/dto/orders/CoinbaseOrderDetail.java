package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import lombok.Getter;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.instrument.Instrument;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseOrderDetail {

  private final String orderId;
  private final String clientOrderId;
  private final String side;
  private final String productId;
  private final String status;
  private final BigDecimal averageFilledPrice;
  private final BigDecimal filledSize;
  private final BigDecimal totalFees;
  private final BigDecimal size;
  private final BigDecimal price;
  private final Date createdTime;

  @JsonCreator
  public CoinbaseOrderDetail(
      @JsonProperty("order_id") String orderId,
      @JsonProperty("client_order_id") String clientOrderId,
      @JsonProperty("side") String side,
      @JsonProperty("product_id") String productId,
      @JsonProperty("status") String status,
      @JsonProperty("average_filled_price") BigDecimal averageFilledPrice,
      @JsonProperty("filled_size") BigDecimal filledSize,
      @JsonProperty("total_fees") BigDecimal totalFees,
      @JsonProperty("size") BigDecimal size,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("created_time") String createdTime
  ) {
    this.orderId = orderId;
    this.clientOrderId = clientOrderId;
    this.side = side;
    this.productId = productId;
    this.status = status;
    this.averageFilledPrice = averageFilledPrice;
    this.filledSize = filledSize;
    this.totalFees = totalFees;
    this.size = size;
    this.price = price;
    this.createdTime = createdTime == null ? null
        : Date.from(DateTimeFormatter.ISO_INSTANT.parse(createdTime, Instant::from));
  }

  public Order.OrderType getOrderType() {
    return CoinbaseAdapters.adaptOrderType(side);
  }

  public Instrument getInstrument() {
    return CoinbaseAdapters.adaptInstrument(productId);
  }
}


