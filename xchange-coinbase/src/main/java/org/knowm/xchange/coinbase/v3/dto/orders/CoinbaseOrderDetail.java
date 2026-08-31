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

/**
 * Current Advanced Trade historical/open-order representation.
 *
 * <p>Futures quantities remain decimal contract quantities. Status, product type, order type and
 * fill accounting are retained exactly as returned so reconciliation can make REST authoritative.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseOrderDetail {

  private final String orderId;
  private final String clientOrderId;
  private final String side;
  private final String productId;
  private final String productType;
  private final String status;
  private final String orderType;
  private final String timeInForce;
  private final String leverage;
  private final CoinbaseMarginType marginType;
  private final CoinbaseOrderConfiguration orderConfiguration;
  private final BigDecimal completionPercentage;
  private final BigDecimal averageFilledPrice;
  private final BigDecimal filledSize;
  private final BigDecimal numberOfFills;
  private final BigDecimal filledValue;
  private final BigDecimal totalFees;
  private final BigDecimal totalValueAfterFees;
  private final BigDecimal size;
  private final BigDecimal price;
  private final boolean pendingCancel;
  private final boolean sizeInQuote;
  private final boolean sizeInclusiveOfFees;
  private final boolean settled;
  private final Date createdTime;
  private final Date lastFillTime;
  private final Date lastUpdateTime;
  private final String rejectReason;
  private final String rejectMessage;
  private final String cancelMessage;
  private final String orderPlacementSource;
  private final String retailPortfolioId;

  /**
   * Creates a legacy minimal order representation.
   *
   * @deprecated use the full-field constructor so exchange-provided order metadata is retained
   */
  @Deprecated
  public CoinbaseOrderDetail(String orderId, String clientOrderId, String side, String productId,
      String status, BigDecimal averageFilledPrice, BigDecimal filledSize, BigDecimal totalFees,
      BigDecimal size, BigDecimal price, String createdTime) {
    this(
        orderId, clientOrderId, side, productId, null, status, null, null, null, null, null, null,
        averageFilledPrice, filledSize, null, null, totalFees, null, size, price, false, false, false,
        false, createdTime, null, null, null, null, null, null, null);
  }

  /**
   * Creates a complete Advanced Trade order response.
   *
   * @param orderId exchange order identifier
   * @param clientOrderId client order identifier
   * @param side exchange order side
   * @param productId canonical product identifier
   * @param productType exchange product classification
   * @param status exchange order status
   * @param orderType raw exchange order type
   * @param timeInForce raw time-in-force classification
   * @param leverage configured leverage
   * @param marginType configured margin type
   * @param orderConfiguration nested order configuration
   * @param completionPercentage completed percentage
   * @param averageFilledPrice average fill price
   * @param filledSize filled quantity
   * @param numberOfFills fill count
   * @param filledValue filled notional
   * @param totalFees aggregate fees
   * @param totalValueAfterFees net value after fees
   * @param size requested quantity
   * @param price requested price
   * @param pendingCancel whether cancellation is pending
   * @param sizeInQuote whether size uses quote units
   * @param sizeInclusiveOfFees whether size includes fees
   * @param settled whether the order settled
   * @param createdTime creation timestamp
   * @param lastFillTime last-fill timestamp
   * @param lastUpdateTime last-update timestamp
   * @param rejectReason structured rejection reason
   * @param rejectMessage rejection message
   * @param cancelMessage cancellation message
   * @param orderPlacementSource order placement source
   * @param retailPortfolioId portfolio identifier
   */
  @JsonCreator
  public CoinbaseOrderDetail(
      @JsonProperty("order_id") String orderId,
      @JsonProperty("client_order_id") String clientOrderId,
      @JsonProperty("side") String side,
      @JsonProperty("product_id") String productId,
      @JsonProperty("product_type") String productType,
      @JsonProperty("status") String status,
      @JsonProperty("order_type") String orderType,
      @JsonProperty("time_in_force") String timeInForce,
      @JsonProperty("leverage") String leverage,
      @JsonProperty("margin_type") CoinbaseMarginType marginType,
      @JsonProperty("order_configuration") CoinbaseOrderConfiguration orderConfiguration,
      @JsonProperty("completion_percentage") BigDecimal completionPercentage,
      @JsonProperty("average_filled_price") BigDecimal averageFilledPrice,
      @JsonProperty("filled_size") BigDecimal filledSize,
      @JsonProperty("number_of_fills") BigDecimal numberOfFills,
      @JsonProperty("filled_value") BigDecimal filledValue,
      @JsonProperty("total_fees") BigDecimal totalFees,
      @JsonProperty("total_value_after_fees") BigDecimal totalValueAfterFees,
      @JsonProperty("size") BigDecimal size,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("pending_cancel") boolean pendingCancel,
      @JsonProperty("size_in_quote") boolean sizeInQuote,
      @JsonProperty("size_inclusive_of_fees") boolean sizeInclusiveOfFees,
      @JsonProperty("settled") boolean settled,
      @JsonProperty("created_time") String createdTime,
      @JsonProperty("last_fill_time") String lastFillTime,
      @JsonProperty("last_update_time") String lastUpdateTime,
      @JsonProperty("reject_reason") String rejectReason,
      @JsonProperty("reject_message") String rejectMessage,
      @JsonProperty("cancel_message") String cancelMessage,
      @JsonProperty("order_placement_source") String orderPlacementSource,
      @JsonProperty("retail_portfolio_id") String retailPortfolioId) {
    this.orderId = orderId;
    this.clientOrderId = clientOrderId;
    this.side = side;
    this.productId = productId;
    this.productType = productType;
    this.status = status;
    this.orderType = orderType;
    this.timeInForce = timeInForce;
    this.leverage = leverage;
    this.marginType = marginType;
    this.orderConfiguration = orderConfiguration;
    this.completionPercentage = completionPercentage;
    this.averageFilledPrice = averageFilledPrice;
    this.filledSize = filledSize;
    this.numberOfFills = numberOfFills;
    this.filledValue = filledValue;
    this.totalFees = totalFees;
    this.totalValueAfterFees = totalValueAfterFees;
    this.size = size;
    this.price = price;
    this.pendingCancel = pendingCancel;
    this.sizeInQuote = sizeInQuote;
    this.sizeInclusiveOfFees = sizeInclusiveOfFees;
    this.settled = settled;
    this.createdTime = parseTimestamp(createdTime);
    this.lastFillTime = parseTimestamp(lastFillTime);
    this.lastUpdateTime = parseTimestamp(lastUpdateTime);
    this.rejectReason = rejectReason;
    this.rejectMessage = rejectMessage;
    this.cancelMessage = cancelMessage;
    this.orderPlacementSource = orderPlacementSource;
    this.retailPortfolioId = retailPortfolioId;
  }

  private static Date parseTimestamp(String value) {
    return value == null || value.isBlank()
        ? null : Date.from(DateTimeFormatter.ISO_INSTANT.parse(value, Instant::from));
  }

  /** @return an XChange order type inferred from the API side. */
  public Order.OrderType getOrderType() { return CoinbaseAdapters.adaptOrderType(side); }

  /**
   * Returns the order classification exactly as supplied by Coinbase's {@code order_type} field.
   *
   * @return exchange order classification such as {@code MARKET} or {@code LIMIT}, or {@code null}
   *     when absent from the response
   * @since 1.0.2
   */
  public String getExchangeOrderType() {
    return orderType;
  }

  /** @return the instrument identified by the canonical product id. */
  public Instrument getInstrument() { return CoinbaseAdapters.adaptInstrument(productId); }
}
