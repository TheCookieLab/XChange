package org.knowm.xchange.coinbasederivatives.service;

import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.client.CoinbaseDerivativesJsonRpcTransport;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

/** Shared service base bound to the exchange-owned JSON-RPC transport. */
public abstract class CoinbaseDerivativesBaseService
    extends BaseExchangeService<CoinbaseDerivativesExchange> implements BaseService {
  protected final CoinbaseDerivativesJsonRpcTransport transport;

  protected CoinbaseDerivativesBaseService(CoinbaseDerivativesExchange exchange) {
    super(exchange);
    transport = exchange.getJsonRpcTransport();
  }
}
