package org.knowm.xchange.cryptocom;

import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComRequestException;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.CryptoComRetryClass;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.NonceException;
import org.knowm.xchange.exceptions.RateLimitExceededException;

/**
 * Maps Crypto.com Exchange v1 numeric error codes (see "Common API Reference" in the API docs) to
 * XChange exceptions.
 */
public class CryptoComErrorAdapter {

  public static final int NO_POSITION = 213;
  public static final int INVALID_NONCE = 10004;
  public static final int TOO_MANY_REQUESTS = 429;
  public static final int DUPLICATE_CLIENT_OID = 315;
  public static final int INSUFFICIENT_AVAILABLE_BALANCE = 20007;
  public static final int EXCEED_MAX_TRADABLE_AMOUNT = 316;

  public static ExchangeException adaptError(CryptoComResponse response) {
    ExchangeException known = mapKnownCode(response.getCode(), response.getMessage());
    if (known != null) {
      return known;
    }
    return new ExchangeException(
        String.format("Crypto.com code: %d error: %s", response.getCode(), response.getMessage()));
  }

  /** Adapts the HTTP-status-level counterpart of {@link #adaptError(CryptoComResponse)}: a non-2xx
   * response deserializes into a {@link CryptoComException} instead of a {@link CryptoComResponse},
   * but carries the same {@code code}/{@code message} error envelope. {@link
   * CryptoComException#getMessage()} already embeds the code, so it's used as-is for the
   * default/unmapped case rather than wrapped again.
   */
  public static ExchangeException adaptError(CryptoComException exception) {
    ExchangeException known = mapKnownCode(exception.getCode(), exception.getMessage());
    if (known != null) {
      return known;
    }
    return new ExchangeException(exception.getMessage(), exception);
  }

  /**
   * Context-aware variant used by the call interceptor: every provider failure surfaces as a
   * structured {@link CryptoComRequestException} carrying the envelope request id, API method,
   * retry classification and HTTP status, so raw and typed callers can correlate failures with
   * their requests and react to the retry class uniformly. (The dedicated XChange exception types
   * remain available through the context-free {@link #adaptError} overloads.)
   */
  public static ExchangeException adaptError(
      CryptoComException exception, long requestId, String method) {
    return new CryptoComRequestException(
        CryptoComRequestException.builder()
            .requestId(requestId)
            .method(method)
            .providerCode(exception.getCode())
            .providerMessage(exception.getMessage())
            .httpStatus(
                exception.getHttpStatusCode() == 0 ? null : exception.getHttpStatusCode())
            .retryClass(classifyRetry(exception.getCode()))
            .cause(exception)
            .message(exception.getMessage()));
  }

  /** Context-aware envelope-error variant (see the exception variant above). */
  public static ExchangeException adaptError(
      CryptoComResponse response, long requestId, String method) {
    return new CryptoComRequestException(
        CryptoComRequestException.builder()
            .requestId(requestId)
            .method(method)
            .providerCode(response.getCode())
            .providerMessage(response.getMessage())
            .retryClass(classifyRetry(response.getCode()))
            .message(
                String.format(
                    "Crypto.com code: %d error: %s", response.getCode(), response.getMessage())));
  }

  /** Maps error codes to their retry classification (conservative: NONE unless clearly retryable). */
  private static CryptoComRetryClass classifyRetry(int code) {
    if (code == TOO_MANY_REQUESTS) {
      return CryptoComRetryClass.RATE_LIMIT;
    }
    if (code == INVALID_NONCE) {
      return CryptoComRetryClass.AUTH;
    }
    if (code == NO_POSITION
        || code == DUPLICATE_CLIENT_OID
        || code == INSUFFICIENT_AVAILABLE_BALANCE
        || code == EXCEED_MAX_TRADABLE_AMOUNT) {
      return CryptoComRetryClass.REJECTED;
    }
    return CryptoComRetryClass.NONE;
  }

  /** Maps the error codes with a dedicated XChange exception type; {@code null} if unmapped. */
  private static ExchangeException mapKnownCode(int code, String message) {
    switch (code) {
      case INSUFFICIENT_AVAILABLE_BALANCE:
      case EXCEED_MAX_TRADABLE_AMOUNT:
        return new FundsExceededException(message);
      case TOO_MANY_REQUESTS:
        return new RateLimitExceededException(message);
      case INVALID_NONCE:
        return new NonceException(message);
      default:
        return null;
    }
  }
}
