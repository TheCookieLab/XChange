package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
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
    params.addCurrencyPair(CurrencyPair.ETH_USD);

    UserTrades tradeHistory = tradeService.getTradeHistory(params);

    assertFalse(tradeHistory.getTrades().isEmpty());
  }

  @Test
  public void testGetOpenOrders() throws Exception {
    Assume.assumeNotNull(tradeService.authTokenCreator);

    OpenOrders oo = tradeService.getOpenOrders();
    assertNotNull(oo);
  }

  @Test
  public void testGetOrderByIdIntegration() throws Exception {
    Assume.assumeNotNull(tradeService.authTokenCreator);

    // Smoke: call getOrder with a dummy id; only assert no exceptions and non-null structure when available
    // In real runs, replace with a known recent order id if available via env config
    String maybeOrderId = System.getProperty("COINBASE_V3_TEST_ORDER_ID");
    org.junit.Assume.assumeTrue(maybeOrderId != null && !maybeOrderId.isEmpty());

    DefaultQueryOrderParam param = new DefaultQueryOrderParam(maybeOrderId);
    assertNotNull(tradeService.getOrder(param));
  }
}
