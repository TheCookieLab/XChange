package org.knowm.xchange.coinbasederivatives.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.client.ReplaySafety;
import org.knowm.xchange.coinbasederivatives.dto.account.CoinbaseDerivativesAccountSummary;
import org.knowm.xchange.coinbasederivatives.dto.account.CoinbaseDerivativesPosition;

/** Exchange-specific private account and position operations. */
public class CoinbaseDerivativesAccountServiceRaw extends CoinbaseDerivativesBaseService {
  public CoinbaseDerivativesAccountServiceRaw(CoinbaseDerivativesExchange exchange) {
    super(exchange);
  }

  public CoinbaseDerivativesAccountSummary getAccountSummary(String currency, boolean extended)
      throws IOException {
    return transport.callPrivate(
        "private/get_account_summary",
        Map.of("currency", currency, "extended", extended),
        CoinbaseDerivativesAccountSummary.class,
        ReplaySafety.READ);
  }

  public List<CoinbaseDerivativesPosition> getPositions(String currency, String kind)
      throws IOException {
    Map<String, Object> params = new ConcurrentHashMap<>();
    if (currency != null) {
      params.put("currency", currency);
    }
    if (kind != null) {
      params.put("kind", kind);
    }
    return Arrays.asList(
        transport.callPrivate(
            "private/get_positions",
            params,
            CoinbaseDerivativesPosition[].class,
            ReplaySafety.READ));
  }
}
