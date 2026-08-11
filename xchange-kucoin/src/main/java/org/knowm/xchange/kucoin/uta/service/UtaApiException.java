package org.knowm.xchange.kucoin.uta.service;

import org.knowm.xchange.kucoin.KucoinApiMode;

/**
 * Structured UTA failure carrying provider context.
 *
 * <p>Per the CF-449 contract, provider failures are mapped into exceptions carrying the API mode,
 * domain/endpoint, provider code, HTTP status, sanitized request/order identity, and a retry
 * classification. Secret material is never included: {@link #toString()} renders through {@link
 * UtaRedaction#sanitize(String)}.
 */
public class UtaApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public enum RetryClassification {
    /** Safe to retry: rate limiting, transient provider unavailability, transport timeout. */
    RETRYABLE,
    /** Never retry: validation, auth, or state errors that will fail identically. */
    NON_RETRYABLE,
    /** Outcome unknown: the request may have been transmitted; reconciliation is required. */
    UNKNOWN_OUTCOME
  }

  private final String code;
  private final KucoinApiMode mode;
  private final String domain;
  private final String endpoint;
  private final Integer httpStatus;
  private final String clientOrderId;
  private final String orderId;
  private final RetryClassification retryClassification;

  public UtaApiException(
      String message,
      String code,
      KucoinApiMode mode,
      String domain,
      String endpoint,
      Integer httpStatus,
      String clientOrderId,
      String orderId,
      RetryClassification retryClassification) {
    super(message);
    this.code = code;
    this.mode = mode;
    this.domain = domain;
    this.endpoint = endpoint;
    this.httpStatus = httpStatus;
    this.clientOrderId = clientOrderId;
    this.orderId = orderId;
    this.retryClassification = retryClassification;
  }

  public UtaApiException(
      String message,
      Throwable cause,
      KucoinApiMode mode,
      String domain,
      String endpoint,
      RetryClassification retryClassification) {
    super(message, cause);
    this.code = null;
    this.mode = mode;
    this.domain = domain;
    this.endpoint = endpoint;
    this.httpStatus = null;
    this.clientOrderId = null;
    this.orderId = null;
    this.retryClassification = retryClassification;
  }

  public String getCode() {
    return code;
  }

  public KucoinApiMode getMode() {
    return mode;
  }

  public String getDomain() {
    return domain;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  /** Sanitized client-supplied order id, or {@code null} when the call carried no order identity. */
  public String getClientOrderId() {
    return clientOrderId;
  }

  /** Sanitized provider order id, or {@code null} when unknown. */
  public String getOrderId() {
    return orderId;
  }

  public RetryClassification getRetryClassification() {
    return retryClassification;
  }

  @Override
  public String toString() {
    return "UtaApiException{"
        + "mode="
        + mode
        + ", domain='"
        + domain
        + '\''
        + ", endpoint='"
        + endpoint
        + '\''
        + ", code='"
        + code
        + '\''
        + ", httpStatus="
        + httpStatus
        + ", clientOrderId='"
        + clientOrderId
        + '\''
        + ", orderId='"
        + orderId
        + '\''
        + ", retryClassification="
        + retryClassification
        + ", message='"
        + UtaRedaction.sanitize(getMessage())
        + '\''
        + '}';
  }
}
