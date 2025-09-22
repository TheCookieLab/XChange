package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.service.trade.TradeService;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeService extends CoinbaseTradeServiceRaw implements TradeService {

  public CoinbaseTradeService(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseTradeService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseTradeService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }
}
