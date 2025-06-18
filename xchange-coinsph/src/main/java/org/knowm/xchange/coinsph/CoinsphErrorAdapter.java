package org.knowm.xchange.coinsph;

import org.knowm.xchange.coinsph.dto.CoinsphResponse;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.RateLimitExceededException;

public class CoinsphErrorAdapter {
  public static final int FUNDS_EXCEEDED = -10112;
  public static final int TRADE_LIMIT_EXCEEDED = -1131;
  public static final int RATE_LIMIT_EXCEEDED = -1003;

  public static ExchangeException adaptError(CoinsphResponse response) {
    switch (response.getCode()) {
      case FUNDS_EXCEEDED:
      case TRADE_LIMIT_EXCEEDED:
        return new FundsExceededException(response.getMessage());
      case RATE_LIMIT_EXCEEDED:
        return new RateLimitExceededException(response.getMessage());
    }
    return new ExchangeException(
        String.format("Coinsph code: %d error: %s", response.getCode(), response.getMessage()));
  }
}
