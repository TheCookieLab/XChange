package org.knowm.xchange.coinbase.v3.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Digest;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseBaseService extends BaseExchangeService implements BaseService {

  protected final CoinbaseAuthenticated coinbaseAdvancedTrade;
  protected final Coinbase coinbasePublic;
  protected final ParamsDigest authTokenCreator;

  protected CoinbaseBaseService(Exchange exchange) {
    this(exchange, ExchangeRestProxyBuilder.forInterface(CoinbaseAuthenticated.class,
            exchange.getExchangeSpecification()).build(),
        CoinbaseV3Digest.createInstance(exchange.getExchangeSpecification().getApiKey(),
            exchange.getExchangeSpecification().getSecretKey()),
        createPublicClient(exchange));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    this(exchange, coinbaseAdvancedTrade,
        CoinbaseV3Digest.createInstance(exchange.getExchangeSpecification().getApiKey(),
            exchange.getExchangeSpecification().getSecretKey()),
        createPublicClient(exchange));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    this(exchange, coinbaseAdvancedTrade, authTokenCreator, createPublicClient(exchange));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator, Coinbase coinbasePublic) {
    super(exchange);
    this.coinbaseAdvancedTrade = coinbaseAdvancedTrade;
    this.authTokenCreator = authTokenCreator;
    this.coinbasePublic = coinbasePublic;
  }

  protected boolean hasAuthentication() {
    return authTokenCreator != null;
  }

  private static Coinbase createPublicClient(Exchange exchange) {
    if (exchange == null || exchange.getExchangeSpecification() == null) {
      return null;
    }
    return ExchangeRestProxyBuilder.forInterface(Coinbase.class, exchange.getExchangeSpecification())
        .build();
  }

}
