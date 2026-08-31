package org.knowm.xchange.coinbase.v3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.knowm.xchange.coinbase.v3.dto.RetryClassification;

/**
 * Structured unknown-outcome failure for non-replayable Coinbase Advanced Trade operations.
 *
 * <p>Raised when a create/edit/close/convert/allocate request fails at the transport layer after
 * the request may have reached the provider, so the server-side outcome is unknown. Callers must not
 * blindly replay the operation; they should reconcile using identifiers in their declared namespace
 * or surface the ambiguity to the operator. The message is sanitized and never contains key material.
 *
 * @since 1.0
 */
public class CoinbaseUnknownOutcomeException extends IOException {

  private static final long serialVersionUID = 1L;

  private final String operation;
  private final String clientOrderId;
  private final RetryClassification retryClassification;
  private final List<String> orderIds;
  private final List<String> clientOrderIds;
  private final String correlationName;
  private final String correlationId;

  /**
   * Creates an unknown-outcome failure identified by a client order id.
   *
   * @param operation interrupted mutation
   * @param clientOrderId client order identifier supplied with the mutation
   * @param transportFailure transport error that obscured the provider outcome
   */
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
    this.orderIds = Collections.emptyList();
    this.clientOrderIds =
        clientOrderId == null
            ? Collections.emptyList()
            : Collections.singletonList(clientOrderId);
    this.correlationName = null;
    this.correlationId = null;
    this.retryClassification = RetryClassification.AMBIGUOUS;
  }

  /**
   * Creates an unknown-outcome failure identified by a non-order correlation id.
   *
   * @param operation interrupted mutation
   * @param correlationName provider field naming the identifier
   * @param correlationId provider identifier used to reconcile the mutation
   * @param transportFailure transport error that obscured the provider outcome
   */
  public CoinbaseUnknownOutcomeException(
      String operation,
      String correlationName,
      String correlationId,
      IOException transportFailure) {
    super(
        "Coinbase "
            + operation
            + (correlationId == null
                ? ""
                : " (" + correlationName + "=" + correlationId + ")")
            + " outcome is unknown after a transport failure; do not replay blindly, reconcile "
            + "before retrying"
            + (transportFailure == null || transportFailure.getMessage() == null
                ? ""
                : ": " + transportFailure.getMessage()),
        transportFailure);
    this.operation = operation;
    this.clientOrderId = null;
    this.orderIds = Collections.emptyList();
    this.clientOrderIds = Collections.emptyList();
    this.correlationName = correlationName;
    this.correlationId = correlationId;
    this.retryClassification = RetryClassification.AMBIGUOUS;
  }

  /**
   * Creates an unknown-outcome failure for a potentially partial batch cancellation.
   *
   * @param operation interrupted mutation
   * @param orderIds provider order identifiers included in the batch
   * @param clientOrderIds client order identifiers included in the batch
   * @param transportFailure transport error that obscured the provider outcome
   */
  public CoinbaseUnknownOutcomeException(
      String operation,
      List<String> orderIds,
      List<String> clientOrderIds,
      IOException transportFailure) {
    super(
        "Coinbase " + operation + " (order_ids=" + safeIds(orderIds)
            + ", client_order_ids=" + safeIds(clientOrderIds)
            + ") outcome is unknown after a transport failure; do not replay blindly, reconcile "
            + "before retrying"
            + (transportFailure == null || transportFailure.getMessage() == null
                ? ""
                : ": " + transportFailure.getMessage()),
        transportFailure);
    this.operation = operation;
    this.clientOrderId = null;
    this.orderIds = safeIds(orderIds);
    this.clientOrderIds = safeIds(clientOrderIds);
    this.correlationName = null;
    this.correlationId = null;
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

  /** Provider order identifiers included in an ambiguous batch mutation. */
  public List<String> getOrderIds() {
    return orderIds;
  }

  /** Client order identifiers included in an ambiguous batch mutation. */
  public List<String> getClientOrderIds() {
    return clientOrderIds;
  }
  /** Provider field naming a non-order reconciliation identifier, when applicable. */
  public String getCorrelationName() {
    return correlationName;
  }

  /** Non-order provider identifier used to reconcile the interrupted mutation, when applicable. */
  public String getCorrelationId() {
    return correlationId;
  }


  /** Always {@link RetryClassification#AMBIGUOUS}: the provider outcome is unknown. */
  public RetryClassification getRetryClassification() {
    return retryClassification;
  }

  private static List<String> safeIds(List<String> ids) {
    return ids == null || ids.isEmpty()
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(ids));
  }
}
