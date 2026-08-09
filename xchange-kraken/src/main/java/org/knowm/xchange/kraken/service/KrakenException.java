package org.knowm.xchange.kraken.service;

import java.util.Arrays;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Structured Kraken failure carrying the provider error array, the failing operation, a retry
 * classification, and redacted detail text.
 *
 * <p>Created by {@link KrakenBaseService#checkResult(org.knowm.xchange.kraken.dto.KrakenResult)}
 * when no more specific typed exception (for example {@link
 * org.knowm.xchange.exceptions.NonceException}) applies.
 */
public class KrakenException extends ExchangeException {

  /** Retry guidance derived from the provider error codes. */
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
   * @param domain exchange domain, for example {@code "spot"}
   * @param operation failing operation, for example {@code "getKrakenLedgerInfo"}
   * @param retryClass retry classification for the first provider error code
   * @param errors sanitized provider error array
   */
  public KrakenException(String domain, String operation, RetryClass retryClass, String[] errors) {
    super(
        "Kraken "
            + domain
            + " error in "
            + operation
            + ": "
            + KrakenRedactor.sanitize(String.join("; ", errors)));
    this.domain = domain;
    this.operation = operation;
    this.retryClass = retryClass;
    this.errors = Arrays.stream(errors).map(KrakenRedactor::sanitize).toArray(String[]::new);
  }

  /**
   * @return exchange domain the failure occurred in
   */
  public String getDomain() {
    return domain;
  }

  /**
   * @return operation that failed
   */
  public String getOperation() {
    return operation;
  }

  /**
   * @return retry classification for the first provider error code
   */
  public RetryClass getRetryClass() {
    return retryClass;
  }

  /**
   * @return sanitized provider error codes/messages
   */
  public String[] getErrors() {
    return errors;
  }

  /**
   * Classifies a Kraken error code into retry guidance.
   *
   * @param errorCode first provider error code
   * @return retry class; {@link RetryClass#UNKNOWN} when no code matched
   */
  public static RetryClass classify(String errorCode) {
    String code = errorCode == null ? "" : errorCode;
    if (code.contains("Rate limit")
        || code.contains("Too many requests")
        || code.contains("Temporary lockout")) {
      return RetryClass.RETRYABLE_RATE_LIMIT;
    }
    if (code.startsWith("EService:")) {
      return RetryClass.RETRYABLE_TRANSIENT;
    }
    if (code.startsWith("EOrder:") || code.startsWith("EAPI:") || code.startsWith("EGeneral:")) {
      return RetryClass.NON_RETRYABLE;
    }
    return RetryClass.UNKNOWN;
  }
}
