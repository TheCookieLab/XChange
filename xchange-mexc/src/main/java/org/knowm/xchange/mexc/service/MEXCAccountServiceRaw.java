package org.knowm.xchange.mexc.service;

import java.io.IOException;
import java.util.Map;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.mexc.dto.MEXCResult;
import org.knowm.xchange.mexc.dto.account.MEXCBalance;

/**
 * @deprecated MEXC Spot v2 ({@code /open/api/v2}) is frozen for compatibility; use the Spot v3
 *     implementation in {@code org.knowm.xchange.mexc.v3} instead. See the xchange-mexc README
 *     migration notes for the removal policy.
 */
@Deprecated
public class MEXCAccountServiceRaw extends MEXCBaseService {
  public MEXCAccountServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public MEXCResult<Map<String, MEXCBalance>> getWalletBalances() throws IOException {
    return mexcAuthenticated.getWalletBalances(apiKey, nonceFactory, signatureCreator);
  }
}
