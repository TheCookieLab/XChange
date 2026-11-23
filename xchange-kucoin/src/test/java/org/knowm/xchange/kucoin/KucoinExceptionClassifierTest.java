package org.knowm.xchange.kucoin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.ExchangeUnavailableException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.kucoin.dto.response.KucoinResponse;
import org.knowm.xchange.kucoin.service.KucoinApiException;
import si.mazi.rescu.HttpStatusIOException;

class KucoinExceptionClassifierTest {
  private HttpStatusIOException status(int code) {
    HttpStatusIOException exception = mock(HttpStatusIOException.class);
    when(exception.getHttpStatusCode()).thenReturn(code);
    when(exception.getMessage()).thenReturn("HTTP " + code);
    return exception;
  }

  @Test
  void classifiesHttpStatusWithOriginalCause() {
    HttpStatusIOException unauthorized = status(401);
    HttpStatusIOException limited = status(429);
    HttpStatusIOException unavailable = status(503);
    assertSame(unauthorized, assertThrows(ExchangeSecurityException.class,
        () -> KucoinExceptionClassifier.classifyingExceptions(() -> { throw unauthorized; })).getCause());
    assertSame(limited, assertThrows(RateLimitExceededException.class,
        () -> KucoinExceptionClassifier.classifyingExceptions(() -> { throw limited; })).getCause());
    assertSame(unavailable, assertThrows(ExchangeUnavailableException.class,
        () -> KucoinExceptionClassifier.classifyingExceptions(() -> { throw unavailable; })).getCause());
  }

  @Test
  void unrelatedTransportFailuresAreNotReinterpreted() {
    HttpStatusIOException badRequest = status(400);
    HttpStatusIOException paymentRequired = status(402);
    IOException timeout = new IOException("network failure");
    assertSame(badRequest, assertThrows(HttpStatusIOException.class,
        () -> KucoinExceptionClassifier.classifyingExceptions(() -> { throw badRequest; })));
    assertSame(paymentRequired, assertThrows(HttpStatusIOException.class,
        () -> KucoinExceptionClassifier.classifyingExceptions(() -> { throw paymentRequired; })));
    assertSame(timeout, assertThrows(IOException.class,
        () -> KucoinExceptionClassifier.classifyingExceptions(() -> { throw timeout; })));
  }

  @Test
  void successfulResponsesAndExchangeErrorCodesRemainSupported() throws Exception {
    KucoinResponse<String> response = new KucoinResponse<>();
    response.setCode("200000");
    response.setData("ok");
    assertEquals("ok", KucoinExceptionClassifier.classifyingExceptions(() -> response));
    KucoinApiException limited = new KucoinApiException("429000", "Too Many Requests");
    assertSame(limited, assertInstanceOf(RateLimitExceededException.class,
        KucoinExceptionClassifier.classify(limited)).getCause());
    assertInstanceOf(ExchangeSecurityException.class,
        KucoinExceptionClassifier.classify(new KucoinApiException("400003", "Invalid API key")));
  }
}
