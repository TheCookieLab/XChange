package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Per-order result returned by Coinbase Advanced Trade batch cancellation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseCancelOrderResult {

  private final Boolean success;
  private final String failureReason;
  private final String orderId;

  /**
   * Creates a batch-cancellation result from the Coinbase wire response.
   *
   * @param success whether Coinbase accepted cancellation of this order
   * @param failureReason provider failure reason when cancellation was not accepted
   * @param orderId provider order identifier
   */
  @JsonCreator
  public CoinbaseCancelOrderResult(
      @JsonProperty("success") Boolean success,
      @JsonProperty("failure_reason") String failureReason,
      @JsonProperty("order_id") String orderId) {
    this.success = success;
    this.failureReason = failureReason;
    this.orderId = orderId;
  }

  /** Returns whether Coinbase accepted cancellation of this order. */
  public boolean isSuccess() {
    return Boolean.TRUE.equals(success);
  }

  /** Returns the provider failure reason, or {@code null} after successful cancellation. */
  public String getFailureReason() {
    return failureReason;
  }

  /** Returns the provider order identifier. */
  public String getOrderId() {
    return orderId;
  }
}
