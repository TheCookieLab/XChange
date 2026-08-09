package org.knowm.xchange.kraken.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of the Kraken AmendOrder endpoint.
 *
 * <p>AmendOrder modifies a live order in place, preserving the Kraken and client order
 * identifiers where possible. The response carries the amend transaction id, the order
 * identifiers after the amend, and any rejection reason.
 */
public class KrakenAmendOrderResponse {

  private final String amendId;
  private final String orderId;
  private final String clientOrderId;
  private final String newOrderId;
  private final String newClientOrderId;
  private final String status;
  private final String rejectReason;
  private final List<String> eventErrors;

  public KrakenAmendOrderResponse(
      @JsonProperty("amendid") String amendId,
      @JsonProperty("order_id") String orderId,
      @JsonProperty("cl_ord_id") String clientOrderId,
      @JsonProperty("new_order_id") String newOrderId,
      @JsonProperty("new_cl_ord_id") String newClientOrderId,
      @JsonProperty("status") String status,
      @JsonProperty("reject_reason") String rejectReason,
      @JsonProperty("event_errors") List<String> eventErrors) {

    this.amendId = amendId;
    this.orderId = orderId;
    this.clientOrderId = clientOrderId;
    this.newOrderId = newOrderId;
    this.newClientOrderId = newClientOrderId;
    this.status = status;
    this.rejectReason = rejectReason;
    this.eventErrors = eventErrors;
  }

  /** @return unique Kraken identifier generated for the amend transaction */
  public String getAmendId() {
    return amendId;
  }

  /** @return Kraken identifier of the amended order, when populated in the request */
  public String getOrderId() {
    return orderId;
  }

  /** @return client identifier of the amended order, when populated in the request */
  public String getClientOrderId() {
    return clientOrderId;
  }

  /** @return new Kraken order id when the amend effectively replaced the order */
  public String getNewOrderId() {
    return newOrderId;
  }

  /** @return new client order id when the amend effectively replaced the order */
  public String getNewClientOrderId() {
    return newClientOrderId;
  }

  /** @return status of the amend operation */
  public String getStatus() {
    return status;
  }

  /** @return rejection reason, when the amend was rejected */
  public String getRejectReason() {
    return rejectReason;
  }

  /** @return non-fatal event warnings, when present */
  public List<String> getEventErrors() {
    return eventErrors;
  }

  @Override
  public String toString() {
    return "KrakenAmendOrderResponse [amendId="
        + amendId
        + ", orderId="
        + orderId
        + ", clientOrderId="
        + clientOrderId
        + ", newOrderId="
        + newOrderId
        + ", newClientOrderId="
        + newClientOrderId
        + ", status="
        + status
        + ", rejectReason="
        + rejectReason
        + ", eventErrors="
        + eventErrors
        + "]";
  }
}
