package org.knowm.xchange.coinbasederivatives.client;

import org.knowm.xchange.exceptions.ExchangeException;

/** Sanitized structured failure from the Coinbase derivatives gateway. */
public class CoinbaseDerivativesException extends ExchangeException {
  private final int code;
  private final Long requestId;
  private final String method;
  private final RetryClassification retryClassification;
  private final String sanitizedDetails;

  public CoinbaseDerivativesException(
      int code,
      String message,
      Long requestId,
      String method,
      RetryClassification retryClassification,
      String sanitizedDetails) {
    super(message);
    this.code = code;
    this.requestId = requestId;
    this.method = method;
    this.retryClassification = retryClassification;
    this.sanitizedDetails = sanitizedDetails;
  }

  public CoinbaseDerivativesException(
      int code,
      String message,
      Long requestId,
      String method,
      RetryClassification retryClassification,
      String sanitizedDetails,
      Throwable cause) {
    super(message, cause);
    this.code = code;
    this.requestId = requestId;
    this.method = method;
    this.retryClassification = retryClassification;
    this.sanitizedDetails = sanitizedDetails;
  }

  public int getCode() {
    return code;
  }

  public Long getRequestId() {
    return requestId;
  }

  public String getMethod() {
    return method;
  }

  public RetryClassification getRetryClassification() {
    return retryClassification;
  }

  public String getSanitizedDetails() {
    return sanitizedDetails;
  }
}
