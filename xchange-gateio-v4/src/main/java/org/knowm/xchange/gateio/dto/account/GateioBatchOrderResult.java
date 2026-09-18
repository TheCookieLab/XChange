package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.knowm.xchange.gateio.dto.trade.GateioSpotOrderResponse;

/**
 * One element of a batch order response (POST /spot/batch_orders or POST /spot/amend_batch_orders).
 *
 * <p>Gate's {@code BatchOrder} element is flat: the complete order (see {@link GateioSpotOrderResponse}) next
 * to the outcome fields. {@code succeeded} marks the per-item result and {@code label}/{@code
 * message} carry failure classification. {@link #getId()} is the placed order's id; {@link
 * #getOrderId()} is only present for amendments and references the amended order. Extends {@link
 * GateioSpotOrderResponse} so order details stay available under the same accessors as a plain order.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GateioBatchOrderResult extends GateioSpotOrderResponse {

  @JsonProperty("order_id")
  String orderId;

  @JsonProperty("succeeded")
  Boolean succeeded;

  @JsonProperty("label")
  String label;

  @JsonProperty("message")
  String message;
}
