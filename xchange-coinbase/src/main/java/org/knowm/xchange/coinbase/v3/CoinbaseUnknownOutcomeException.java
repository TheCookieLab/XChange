package org.knowm.xchange.coinbase.v3;

import java.io.IOException;
import org.knowm.xchange.coinbase.v3.dto.RetryClassification;

/**
 * Structured unknown-outcome failure for non-replayable Coinbase Advanced Trade operations.
 *
 * <p>Raised when a create/edit/close/convert/allocate request fails at the transport layer after
 * the request may have reached the provider, so the server-side outcome is unknown. Callers must not
 * blindly replay the operation; they should reconcile by {@code client_order_id} or surface the
 * ambiguity to the operator. The message is sanitized and never contains key material.
 *
 * @since 1.0
 */
public class CoinbaseUnknownOutcomeException extends IOException {

  private static final long serialVersionUID = 1L;

  private final String operation;
  private final String clientOrderId;
  private final RetryClassification retryClassification;

  public CoinbaseUnknownOutcomeException(
      String operation, String clientOrderId, IOException transportFailure) {
    super(
        "Coinbase "
            + operation
            + (clientOrderId == null ? "" : " (client_order_id=" + clientOrderId + ")")
            + " outcome is unknown after a transport failure; do not replay blindly, reconcile "
            + "before retrying"
            + (transportFailure == null || transportFailure.getMessage() == null
                ? ""
                : ": " + transportFailure.getMessage()),
        transportFailure);
    this.operation = operation;
    this.clientOrderId = clientOrderId;
    this.retryClassification = RetryClassification.AMBIGUOUS;
  }

  /** The operation that was interrupted (for example {@code createOrder}). */
  public String getOperation() {
    return operation;
  }

  /** The {@code client_order_id} of the interrupted request, when one was supplied. */
  public String getClientOrderId() {
    return clientOrderId;
  }

  /** Always {@link RetryClassification#AMBIGUOUS}: the provider outcome is unknown. */
  public RetryClassification getRetryClassification() {
    return retryClassification;
  }
}
