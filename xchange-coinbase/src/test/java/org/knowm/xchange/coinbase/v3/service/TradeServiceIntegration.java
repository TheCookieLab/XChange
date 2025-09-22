package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertFalse;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrades;
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

  @Test
  public void testGetTradeHistory() throws Exception {
    Assume.assumeNotNull(tradeService.authTokenCreator);

    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();

    UserTrades tradeHistory = tradeService.getTradeHistory(params);

    assertFalse(tradeHistory.getTrades().isEmpty());
  }

  @Test
  public void testGetTradeHistoryForMultipleCurrencyPairs() throws Exception {
    Assume.assumeNotNull(tradeService.authTokenCreator);

    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    // Allow only one product_id for now to avoid repeated param encoding
    params.addCurrencyPair(CurrencyPair.BTC_USD);

    UserTrades tradeHistory = tradeService.getTradeHistory(params);

    assertFalse(tradeHistory.getTrades().isEmpty());
  }
}
