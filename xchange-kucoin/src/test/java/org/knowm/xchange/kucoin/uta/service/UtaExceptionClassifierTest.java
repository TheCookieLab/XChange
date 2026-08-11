package org.knowm.xchange.kucoin.uta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification;

class UtaExceptionClassifierTest {

  private static final String DOMAIN = "trade";
  private static final String ENDPOINT = "POST /api/ua/v1/unified/order/place";

  @Test
  void successfulEnvelopeReturnsData() throws IOException {
    UtaResponse<String> response = new UtaResponse<>();
    response.setCode("200000");
    response.setData("ok");
    assertEquals(
        "ok",
        UtaExceptionClassifier.classifyingExceptions(
            () -> response, DOMAIN, ENDPOINT, "co-1", null));
  }

  @Test
  void failureEnvelopeCarriesStructuredContext() {
    UtaResponse<String> response = new UtaResponse<>();
    response.setCode("400003");
    response.setMsg("Invalid API key");
    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () ->
                UtaExceptionClassifier.classifyingExceptions(
                    () -> response, DOMAIN, ENDPOINT, "co-1", null));
    assertEquals("400003", e.getCode());
    assertEquals("co-1", e.getClientOrderId());
    assertEquals(RetryClassification.NON_RETRYABLE, e.getRetryClassification());
    assertEquals(ENDPOINT, e.getEndpoint());
  }

  @Test
  void rateLimitCodeIsRetryable() {
    UtaResponse<String> response = new UtaResponse<>();
    response.setCode("429000");
    response.setMsg("Too Many Requests");
    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () ->
                UtaExceptionClassifier.classifyingExceptions(
                    () -> response, DOMAIN, ENDPOINT, null, null));
    assertEquals(RetryClassification.RETRYABLE, e.getRetryClassification());
    assertTrue(
        UtaExceptionClassifier.mapToExchangeException(e) instanceof RateLimitExceededException);
  }

  @Test
  void securityCodesMapToExchangeSecurityException() {
    UtaResponse<String> response = new UtaResponse<>();
    response.setCode("400001");
    response.setMsg("Invalid");
    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () ->
                UtaExceptionClassifier.classifyingExceptions(
                    () -> response, DOMAIN, ENDPOINT, null, null));
    assertTrue(
        UtaExceptionClassifier.mapToExchangeException(e) instanceof ExchangeSecurityException);
  }

  @Test
  void transportFailureIsClassifiedRetryable() {
    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () ->
                UtaExceptionClassifier.classifyingExceptions(
                    () -> {
                      throw new java.net.SocketTimeoutException("read timed out");
                    },
                    DOMAIN,
                    ENDPOINT,
                    "co-1",
                    null));
    assertEquals(RetryClassification.RETRYABLE, e.getRetryClassification());
    assertEquals(null, e.getCode());
    assertEquals(null, e.getHttpStatus());
  }

  @Test
  void transportFailureToStringIsRedacted() {
    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () ->
                UtaExceptionClassifier.classifyingExceptions(
                    () -> {
                      throw new IOException("secret leak: KC-API-PASSPHRASE=hunter2");
                    },
                    DOMAIN,
                    ENDPOINT,
                    null,
                    null));
    assertTrue(!e.toString().contains("hunter2"));
    assertTrue(e.toString().contains("KC-API-PASSPHRASE=***"));
  }
}
