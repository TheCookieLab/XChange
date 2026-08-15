package org.knowm.xchange.mexc.v3.client;

import org.knowm.xchange.exceptions.CurrencyPairNotValidException;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.ExchangeUnavailableException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.OperationTimeoutException;
import org.knowm.xchange.exceptions.OrderAmountUnderMinimumException;
import org.knowm.xchange.exceptions.OrderNotValidException;
import org.knowm.xchange.exceptions.RateLimitExceededException;

/**
 * Maps MEXC Spot v3 provider failures to the XChange exception hierarchy.
 *
 * <p>Code mapping follows the provider error table; unmapped codes fall back to the HTTP status
 * semantics documented by {@link MexcV3Exception} (401/403 authentication or permission, 418/429
 * rate limiting, 5xx provider-side) before surfacing as a generic {@link ExchangeException}.
 */
public final class MexcV3ErrorAdapter {

  private MexcV3ErrorAdapter() {}

  public static ExchangeException adapt(MexcV3Exception e) {
    String message = e.getMsg();
    if (message == null || message.isEmpty()) {
      message = "MEXC Spot v3 operation failed without an error message (code "
          + e.getCode() + ", http " + e.getHttpStatus() + ").";
    }
    switch (e.getCode()) {
      case 400: // api key required
      case 401: // no authority
      case 602: // signature verification failed
      case 10072: // invalid access key
      case 700001: // api-key format invalid
      case 700002: // signature not valid
      case 700006: // IP not in whitelist
      case 700007: // no permission to access endpoint
        return new ExchangeSecurityException(message, e);
      case 429: // too many requests
        return new RateLimitExceededException(message, e);
      case 10007: // bad symbol
      case 30014: // invalid symbol
      case 30021: // invalid symbol
      case 730001: // pair not found
        return new CurrencyPairNotValidException(message, e);
      case 10101: // insufficient balance
      case 30005: // oversold
        return new FundsExceededException(message, e);
      case 30002: // minimum transaction volume
      case 30003: // invalid price or quantity range
      case 30010: // no valid trade price
        return new OrderAmountUnderMinimumException(message, e);
      case 700003: // timestamp outside recvWindow
        return new OperationTimeoutException(message, e);
      case -2011: // unknown order (cancel/query)
      case 20116: // order does not exist (query by id/client id)
      case 30041: // current order type cannot place order
      case 33333: // wrong order parameters
      case 44444: // insufficient order quantity
      case 700004: // origClientOrderId or orderId both empty
      case 700005: // orderId and origClientOrderId both set
      case 700008: // illegal characters in parameter
        return new OrderNotValidException(message, e);
      case 504: // gateway time-out
      case 503: // service unavailable
        return new ExchangeUnavailableException(message, e);
      default:
        // Fall back to the documented HTTP status semantics when the provider body code is not
        // mapped: 401/403 authentication or permission, 418/429 rate limiting, 5xx provider-side.
        // This keeps rate-limit and availability handling reliable for high-level callers even
        // when a rate-limit or outage response uses a provider-specific body code.
        int httpStatus = e.getHttpStatus();
        if (httpStatus == 401 || httpStatus == 403) {
          return new ExchangeSecurityException(message, e);
        }
        if (httpStatus == 418 || httpStatus == 429) {
          return new RateLimitExceededException(message, e);
        }
        if (httpStatus >= 500) {
          return new ExchangeUnavailableException(message, e);
        }
        return new ExchangeException(message, e);
    }
  }
}
