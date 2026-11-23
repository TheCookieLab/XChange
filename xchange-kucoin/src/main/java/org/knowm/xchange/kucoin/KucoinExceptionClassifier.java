package org.knowm.xchange.kucoin;

import java.io.IOException;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.ExchangeUnavailableException;
import org.knowm.xchange.exceptions.NonceException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.kucoin.dto.response.KucoinResponse;
import org.knowm.xchange.kucoin.service.KucoinApiException;
import si.mazi.rescu.HttpStatusIOException;

public final class KucoinExceptionClassifier {

  KucoinExceptionClassifier() {}

  public static <T> T classifyingExceptions(IOExceptionThrowingSupplier<KucoinResponse<T>> apiCall)
      throws IOException {
    KucoinResponse<T> response;
    try {
      response = apiCall.get();
    } catch (HttpStatusIOException e) {
      switch (e.getHttpStatusCode()) {
        case 401:
          throw new ExchangeSecurityException(e.getMessage(), e);
        case 429:
          throw new RateLimitExceededException(e.getMessage(), e);
        case 503:
          throw new ExchangeUnavailableException(e.getMessage(), e);
        default:
          throw e;
      }
    }
    if (response.isSuccessful()) {
      return response.getData();
    } else {
      throw classify(new KucoinApiException(response.getCode(), response.getMessage()));
    }
  }

  public static RuntimeException classify(KucoinApiException e) {
    if (e.getMessage().equalsIgnoreCase("Service unavailable")) {
      return new ExchangeUnavailableException(e.getMessage(), e);
    }
    if (e.getMessage().contains("check environment variables")) {
      return new ExchangeSecurityException(e.getMessage(), e);
    }

    switch (e.getCode()) {
      case "429000":
        return new RateLimitExceededException(e.getMessage(), e);
      case "400001":
      case "400003":
      case "400004":
      case "400006":
      case "400007":
      case "411100":
        return new ExchangeSecurityException(e.getMessage(), e);
      case "400005":
        return new NonceException(e.getMessage(), e);
      default:
        return new ExchangeException(e.getMessage(), e);
    }
  }

  @FunctionalInterface
  interface IOExceptionThrowingSupplier<T> {
    T get() throws IOException;
  }
}
