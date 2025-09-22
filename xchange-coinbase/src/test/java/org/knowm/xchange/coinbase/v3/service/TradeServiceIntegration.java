package org.knowm.xchange.coinbase.v3.service;

import org.junit.BeforeClass;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.utils.AuthUtils;

public class TradeServiceIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseTradeService tradeService;

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification exchangeSpecification = ExchangeFactory.INSTANCE.createExchange(
        CoinbaseExchange.class).getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exchangeSpecification);
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    tradeService = (CoinbaseTradeService) exchange.getTradeService();
  }
}
