package org.knowm.xchange.krakenfutures.service;

import java.util.Arrays;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Structured Kraken Futures failure carrying the provider error string, the failing operation, a
 * retry classification, and redacted detail text.
 */
public class KrakenFuturesException extends ExchangeException {

  /** Retry guidance derived from the provider error message. */
  public enum RetryClass {
    /** Caller must not retry: invalid input, rejected order, or authentication state error. */
    NON_RETRYABLE,
    /** Provider rate limit or temporary lockout; bounded backoff is appropriate. */
    RETRYABLE_RATE_LIMIT,
    /** Provider-side unavailability; bounded backoff is appropriate. */
    RETRYABLE_TRANSIENT,
    /** No known code matched; inspect the details before retrying. */
    UNKNOWN
  }

  private final String domain;
  private final String operation;
  private final RetryClass retryClass;
  private final String[] errors;

  /**
   * @param domain exchange domain, for example {@code "futures"}
   * @param operation failing operation
   * @param retryClass retry classification for the provider error
   * @param errors sanitized provider error codes/messages
   */
  public KrakenFuturesException(
      String domain, String operation, RetryClass retryClass, String[] errors) {
    super(
        "Kraken "
            + domain
            + " error in "
            + operation
            + ": "
            + KrakenFuturesRedactor.sanitize(String.join("; ", errors)));
    this.domain = domain;
    this.operation = operation;
    this.retryClass = retryClass;
    this.errors = Arrays.stream(errors).map(KrakenFuturesRedactor::sanitize).toArray(String[]::new);
  }

  /** @return exchange domain the failure occurred in */
  public String getDomain() {
    return domain;
  }

  /** @return operation that failed */
  public String getOperation() {
    return operation;
  }

  /** @return retry classification for the provider error */
  public RetryClass getRetryClass() {
    return retryClass;
  }

  /** @return sanitized provider error codes/messages */
  public String[] getErrors() {
    return errors;
  }

  /**
   * Classifies a Kraken Futures error message into retry guidance.
   *
   * @param error provider error message
   * @return retry class; {@link RetryClass#UNKNOWN} when no code matched
   */
  public static RetryClass classify(String error) {
    String code = error == null ? "" : error;
    if (code.contains("Rate limit")
        || code.contains("Too many requests")
        || code.contains("temporary lockout")) {
      return RetryClass.RETRYABLE_RATE_LIMIT;
    }
    if (code.contains("unavailable") || code.contains("busy")) {
      return RetryClass.RETRYABLE_TRANSIENT;
    }
    if (code.contains("insufficient")
        || code.contains("not found")
        || code.contains("invalid")
        || code.contains("rejected")) {
      return RetryClass.NON_RETRYABLE;
    }
    return RetryClass.UNKNOWN;
  }
}
