package org.knowm.xchange.kucoin.uta.service;

import static org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification.NON_RETRYABLE;
import static org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification.RETRYABLE;
import static org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification.UNKNOWN_OUTCOME;

import java.io.IOException;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.ExchangeUnavailableException;
import org.knowm.xchange.exceptions.NonceException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.kucoin.KucoinApiMode;

/**
 * Maps UTA envelope failures and transport errors to typed XChange exceptions.
 *
 * <p>Every failure is surfaced as a {@link UtaApiException} carrying structured context (mode,
 * domain, endpoint, provider code, HTTP status, sanitized order identity, retry classification)
 * before any XChange-level mapping, so callers can inspect provider context losslessly.
 */
public final class UtaExceptionClassifier {

  private UtaExceptionClassifier() {}

  /** Provider codes that indicate authentication or permission failures. */
  private static final String[] SECURITY_CODES = {
    "400001", "400002", "400003", "400004", "400006", "400007", "400008", "411100", "414000"
  };

  public static <T> T classifyingExceptions(
      IOExceptionThrowingSupplier<UtaResponse<T>> apiCall, String domain, String endpoint)
      throws IOException {
    return classifyingExceptions(apiCall, domain, endpoint, null, null);
  }

  public static <T> T classifyingExceptions(
      IOExceptionThrowingSupplier<UtaResponse<T>> apiCall,
      String domain,
      String endpoint,
      String clientOrderId,
      String orderId)
      throws IOException {
    UtaResponse<T> response;
    try {
      response = apiCall.get();
    } catch (IOException | RuntimeException e) {
      throw classifyTransport(e, domain, endpoint, clientOrderId, orderId);
    }
    if (response == null || !response.isSuccessful()) {
      String code = response == null ? null : response.getCode();
      String message = response == null ? null : response.getMessage();
      throw classifyEnvelope(code, message, domain, endpoint, clientOrderId, orderId);
    }
    return response.getData();
  }

  static UtaApiException classifyEnvelope(
      String code,
      String message,
      String domain,
      String endpoint,
      String clientOrderId,
      String orderId) {
    String text = UtaRedaction.sanitize(message == null ? "Unknown UTA error" : message);
    UtaApiException.RetryClassification retry =
        "429000".equals(code) ? RETRYABLE : NON_RETRYABLE;
    return new UtaApiException(
        text, code, KucoinApiMode.UTA, domain, endpoint, null, clientOrderId, orderId, retry);
  }

  static UtaApiException classifyTransport(
      Throwable cause, String domain, String endpoint, String clientOrderId, String orderId) {
    String text = UtaRedaction.sanitize(cause.getMessage());
    UtaApiException.RetryClassification retry = RETRYABLE;
    int httpStatus = -1;
    if (cause instanceof jakarta.ws.rs.ProcessingException
        || cause instanceof java.net.SocketTimeoutException
        || cause instanceof java.net.ConnectException) {
      retry = RETRYABLE;
    } else if (cause instanceof jakarta.ws.rs.WebApplicationException) {
      jakarta.ws.rs.WebApplicationException wae = (jakarta.ws.rs.WebApplicationException) cause;
      httpStatus = wae.getResponse() == null ? -1 : wae.getResponse().getStatus();
      if (httpStatus >= 400 && httpStatus < 500) {
        retry = NON_RETRYABLE;
      }
    }
    UtaApiException uta =
        new UtaApiException(
            text == null ? "UTA transport failure: " + cause.getClass().getSimpleName() : text,
            cause,
            KucoinApiMode.UTA,
            domain,
            endpoint,
            retry);
    return uta;
  }

  /** Maps a classified {@link UtaApiException} to the closest typed XChange exception. */
  public static RuntimeException mapToExchangeException(UtaApiException e) {
    if (e.getRetryClassification() == UtaApiException.RetryClassification.UNKNOWN_OUTCOME) {
      return new ExchangeException(e.getMessage(), e);
    }
    if (e.getHttpStatus() != null && e.getHttpStatus() == 429) {
      return new RateLimitExceededException(e.getMessage(), e);
    }
    if ("429000".equals(e.getCode())) {
      return new RateLimitExceededException(e.getMessage(), e);
    }
    if ("400005".equals(e.getCode())) {
      return new NonceException(e.getMessage(), e);
    }
    if (e.getCode() != null) {
      for (String securityCode : SECURITY_CODES) {
        if (securityCode.equals(e.getCode())) {
          return new ExchangeSecurityException(e.getMessage(), e);
        }
      }
    }
    if (e.getMessage() != null
        && (e.getMessage().toLowerCase().contains("service unavailable")
            || e.getMessage().toLowerCase().contains("maintenance"))) {
      return new ExchangeUnavailableException(e.getMessage(), e);
    }
    return new ExchangeException(e.getMessage(), e);
  }

  /** Wraps a raw call in the classifier and maps the final failure to a typed exception. */
  public static <T> T callOrThrow(
      IOExceptionThrowingSupplier<UtaResponse<T>> apiCall, String domain, String endpoint)
      throws IOException {
    return callOrThrow(apiCall, domain, endpoint, null, null);
  }

  public static <T> T callOrThrow(
      IOExceptionThrowingSupplier<UtaResponse<T>> apiCall,
      String domain,
      String endpoint,
      String clientOrderId,
      String orderId)
      throws IOException {
    try {
      return classifyingExceptions(apiCall, domain, endpoint, clientOrderId, orderId);
    } catch (UtaApiException e) {
      throw mapToExchangeException(e);
    }
  }

  @FunctionalInterface
  public interface IOExceptionThrowingSupplier<T> {
    T get() throws IOException;
  }
}
