package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

/**
 * Unit tests for CoinbaseTradeService.
 * Tests service instantiation and basic structure to prevent regressions.
 */
public class CoinbaseTradeServiceUnitTest {

  @Test
  public void testServiceCreationSucceeds() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    TradeService service = exchange.getTradeService();
    
    assertNotNull("Trade service should not be null", service);
  }

  @Test
  public void testServiceIsCorrectType() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    TradeService service = exchange.getTradeService();
    
    assertNotNull("Service should not be null", service);
    assert(service instanceof CoinbaseTradeService);
  }

  @Test
  public void testCreateTradeHistoryParamsReturnsCorrectType() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    CoinbaseTradeService service = (CoinbaseTradeService) exchange.getTradeService();
    
    TradeHistoryParams params = service.createTradeHistoryParams();
    
    assertNotNull("Trade history params should not be null", params);
    assert(params instanceof CoinbaseTradeHistoryParams);
  }
}

