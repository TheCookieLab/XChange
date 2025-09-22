package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeServiceRaw extends CoinbaseBaseService {

  public CoinbaseTradeServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseTradeServiceRaw(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseTradeServiceRaw(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }
}
