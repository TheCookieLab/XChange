package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.kalshi.KalshiAdapters;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.service.account.AccountService;

/** Generic account service for Kalshi; balances are USD. */
public class KalshiAccountService extends KalshiAccountServiceRaw implements AccountService {

  public KalshiAccountService(KalshiExchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    return KalshiAdapters.adaptAccountInfo(getKalshiBalance());
  }
}
