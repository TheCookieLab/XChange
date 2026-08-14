package org.knowm.xchange.mexc.v3.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.mexc.v3.MexcV3Adapters;
import org.knowm.xchange.mexc.v3.MexcV3Symbols;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.client.ReplaySafety;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3TradeFeeResponse;
import org.knowm.xchange.service.account.AccountService;

/** Account service over the authenticated MEXC Spot v3 REST surface. */
public class MexcV3AccountService extends MexcV3BaseService implements AccountService {

  public MexcV3AccountService(Exchange exchange) {
    super(exchange);
  }

  /** Raw account snapshot ({@code GET /api/v3/account}). */
  public org.knowm.xchange.mexc.v3.dto.account.MexcV3Account getAccountRaw()
      throws IOException, MexcV3Exception {
    return mexcV3Authenticated.account(apiKey, recvWindowMs, timestampFactory, signatureCreator);
  }

  /** Raw trade-fee response for one symbol ({@code GET /api/v3/tradeFee}). */
  public MexcV3TradeFeeResponse getTradeFeeRaw(
      org.knowm.xchange.currency.CurrencyPair pair) throws IOException, MexcV3Exception {
    return mexcV3Authenticated.tradeFee(
        apiKey, MexcV3Symbols.toMexcSymbol(pair), recvWindowMs, timestampFactory, signatureCreator);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    return execute(
        () -> new AccountInfo(MexcV3Adapters.adaptWallet(getAccountRaw())), ReplaySafety.READ);
  }
}
