package org.knowm.xchange.binance.error;

import org.knowm.xchange.binance.config.BinanceProductFamily;
import org.knowm.xchange.binance.dto.BinanceException;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Structured Binance failure carrying product family, endpoint, provider code, retry
 * classification, and client order identity, with a sanitized message.
 *
 * <p>The message never contains secrets: Binance error payloads are provider-owned and may echo
 * request parameters, so the message is passed through {@link BinanceRedaction} before being
 * exposed.
 */
public class BinanceStructuredException extends ExchangeException {

  private final BinanceProductFamily productFamily;
  private final String endpoint;
  private final int code;
  private final int httpStatus;
  private final BinanceRetryClassification retryClassification;
  private final String clientOrderId;

  private BinanceStructuredException(
      String message,
      Throwable cause,
      BinanceProductFamily productFamily,
      String endpoint,
      int code,
      int httpStatus,
      BinanceRetryClassification retryClassification,
      String clientOrderId) {
    super(BinanceRedaction.redact(message), cause);
    this.productFamily = productFamily;
    this.endpoint = endpoint;
    this.code = code;
    this.httpStatus = httpStatus;
    this.retryClassification = retryClassification;
    this.clientOrderId = clientOrderId;
  }

  /** Builds a structured exception from a Binance error payload and invocation context. */
  public static BinanceStructuredException from(
      BinanceException cause,
      BinanceProductFamily productFamily,
      String endpoint,
      String clientOrderId) {
    return new BinanceStructuredException(
        cause.getMessage(),
        cause,
        productFamily,
        endpoint,
        cause.getCode(),
        cause.getHttpStatusCode(),
        BinanceErrorClassifier.classify(cause),
        clientOrderId);
  }

  /** The product family the failing call belonged to. */
  public BinanceProductFamily getProductFamily() {
    return productFamily;
  }

  /** The endpoint the failing call targeted, in the form {@code family/operation}. */
  public String getEndpoint() {
    return endpoint;
  }

  /** Binance provider error code, or {@code 0} when no payload was available. */
  public int getCode() {
    return code;
  }

  /** HTTP status of the failed response. */
  public int getHttpStatus() {
    return httpStatus;
  }

  /** Whether and how this failure may be retried. */
  public BinanceRetryClassification getRetryClassification() {
    return retryClassification;
  }

  /** Client order identity for reconciliation, or {@code null} when not applicable. */
  public String getClientOrderId() {
    return clientOrderId;
  }

  @Override
  public String toString() {
    return "BinanceStructuredException{"
        + "productFamily="
        + productFamily
        + ", endpoint='"
        + endpoint
        + '\''
        + ", code="
        + code
        + ", httpStatus="
        + httpStatus
        + ", retryClassification="
        + retryClassification
        + ", clientOrderId='"
        + clientOrderId
        + '\''
        + ", message='"
        + getMessage()
        + '\''
        + '}';
  }
}
