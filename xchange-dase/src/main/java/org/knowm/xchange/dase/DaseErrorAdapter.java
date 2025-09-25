package org.knowm.xchange.dase;

import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/** Converts DASE error payloads to XChange exceptions. */
public final class DaseErrorAdapter {

  private DaseErrorAdapter() {}

  public static ExchangeException adapt(String type, String message) {
    if (type == null) {
      return new ExchangeException(message);
    }
    switch (type) {
      case "Unauthorized":
        return new ExchangeSecurityException(message);
      case "InsufficientFunds":
        return new FundsExceededException(message);
      case "TooManyRequests":
        return new RateLimitExceededException(message);
      default:
        return new ExchangeException(message);
    }
  }
}


