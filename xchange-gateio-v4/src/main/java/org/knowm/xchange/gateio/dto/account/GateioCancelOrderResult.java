package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One element of a cancellation response (POST /spot/cancel_batch_orders or DELETE /spot/orders).
 *
 * <p>Gate's {@code OrderCancel} element is flat: the complete order (see {@link GateioOrder})
 * alongside the outcome fields. {@code succeeded} marks the per-order result and {@code label}/
 * {@code message} carry failure classification, so callers must inspect {@link #getSucceeded()}
 * before treating an order as cancelled. Extends {@link GateioOrder} so order details stay
 * available under the same accessors as a plain order.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GateioCancelOrderResult extends GateioOrder {

  @JsonProperty("succeeded")
  Boolean succeeded;

  @JsonProperty("label")
  String label;

  @JsonProperty("message")
  String message;
}
