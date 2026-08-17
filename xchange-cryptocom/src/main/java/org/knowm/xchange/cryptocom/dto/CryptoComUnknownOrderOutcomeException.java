package org.knowm.xchange.cryptocom.dto;

/**
 * Raised when an order placement crossed the network but its outcome could not be established:
 * the provider never acknowledged it and reconciliation queries also failed. The placement must
 * NOT be re-sent — the caller owns the ambiguity and must reconcile with the provider operator
 * before acting. Carries the same structured fields as {@link CryptoComRequestException} plus the
 * client reference used for the placement.
 */
public class CryptoComUnknownOrderOutcomeException extends CryptoComRequestException {

  private static final long serialVersionUID = 1L;

  public CryptoComUnknownOrderOutcomeException(
      long requestId,
      String method,
      String clientOid,
      String instrumentName,
      Throwable cause) {
    super(
        CryptoComRequestException.builder()
            .requestId(requestId)
            .method(method)
            .providerCode(cause instanceof CryptoComException ? ((CryptoComException) cause).getCode() : null)
            .clientOid(clientOid)
            .instrumentName(instrumentName)
            .retryClass(CryptoComRetryClass.NONE)
            .cause(cause)
            .message(
                "Order placement outcome unknown (request "
                    + requestId
                    + (clientOid != null ? ", clientOid " + clientOid : "")
                    + " for method "
                    + method
                    + "): the provider never acknowledged the order and reconciliation failed. "
                    + "The order was NOT automatically re-sent; reconcile with the provider before retrying."));
  }
}