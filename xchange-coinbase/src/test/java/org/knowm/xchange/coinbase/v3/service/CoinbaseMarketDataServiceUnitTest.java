package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.service.marketdata.MarketDataService;

/**
 * Unit tests for CoinbaseMarketDataService.
 * Tests service instantiation and basic structure to prevent regressions.
 */
public class CoinbaseMarketDataServiceUnitTest {

  @Test
  public void testServiceCreationSucceeds() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    MarketDataService service = exchange.getMarketDataService();
    
    assertNotNull("Market data service should not be null", service);
  }

  @Test
  public void testServiceIsCorrectType() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    MarketDataService service = exchange.getMarketDataService();
    
    assertNotNull("Service should not be null", service);
    assert(service instanceof CoinbaseMarketDataService);
  }
}

