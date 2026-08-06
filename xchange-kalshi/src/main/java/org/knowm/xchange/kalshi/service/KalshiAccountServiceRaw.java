package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.dto.account.KalshiBalanceResponse;
import org.knowm.xchange.kalshi.dto.account.KalshiPositionsResponse;

/** Raw Kalshi account access returning provider DTOs. */
public class KalshiAccountServiceRaw extends KalshiBaseService {

  protected KalshiAccountServiceRaw(KalshiExchange exchange) {
    super(exchange);
  }

  /** Portfolio balance; the canonical amount is a fixed-point dollar string. */
  public KalshiBalanceResponse getKalshiBalance() throws IOException {
    return kalshiAuthenticated.getBalance(apiKey, timestampFactory(), digest);
  }

  /** Open event and market positions. */
  public KalshiPositionsResponse getKalshiPositions(Integer limit, String cursor)
      throws IOException {
    return kalshiAuthenticated.getPositions(apiKey, timestampFactory(), digest, limit, cursor);
  }
}
