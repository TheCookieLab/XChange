package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.service.account.AccountService;

/** Generic account service for Polymarket; the single wallet holds USD (USDC) collateral. */
public class PolymarketAccountService extends PolymarketAccountServiceRaw
    implements AccountService {

  public PolymarketAccountService(PolymarketExchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    return PolymarketAdapters.adaptAccountInfo(getCollateralBalance());
  }
}
