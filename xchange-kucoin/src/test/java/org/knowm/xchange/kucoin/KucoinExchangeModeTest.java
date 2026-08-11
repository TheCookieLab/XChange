package org.knowm.xchange.kucoin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.kucoin.uta.UtaAccountService;
import org.knowm.xchange.kucoin.uta.UtaMarketDataService;
import org.knowm.xchange.kucoin.uta.UtaTradeService;

/**
 * Classic-versus-UTA compatibility boundary tests: the default stays CLASSIC, UTA is explicit, and
 * selecting the wrong generation fails early and actionably.
 */
class KucoinExchangeModeTest {

  private static KucoinExchange exchangeWith(Object apiMode) {
    KucoinExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(KucoinExchange.class);
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    if (apiMode != null) {
      spec.setExchangeSpecificParametersItem(KucoinExchange.API_MODE_PARAMETER, apiMode);
    }
    exchange.applySpecification(spec);
    return exchange;
  }

  @Test
  void defaultsToClassicMode() {
    KucoinExchange exchange = exchangeWith(null);
    assertEquals(KucoinApiMode.CLASSIC, exchange.getApiMode());
    assertInstanceOf(KucoinMarketDataService.class, exchange.getMarketDataService());
    assertInstanceOf(KucoinAccountService.class, exchange.getAccountService());
    assertInstanceOf(KucoinTradeService.class, exchange.getTradeService());
  }

  @Test
  void explicitClassicKeepsClassicServices() {
    KucoinExchange exchange = exchangeWith("CLASSIC");
    assertEquals(KucoinApiMode.CLASSIC, exchange.getApiMode());
    assertInstanceOf(KucoinMarketDataService.class, exchange.getMarketDataService());
  }

  @Test
  void utaModeWiresUtaServices() {
    KucoinExchange exchange = exchangeWith("UTA");
    assertEquals(KucoinApiMode.UTA, exchange.getApiMode());
    assertInstanceOf(UtaMarketDataService.class, exchange.getUtaMarketDataService());
    assertInstanceOf(UtaAccountService.class, exchange.getUtaAccountService());
    assertInstanceOf(UtaTradeService.class, exchange.getUtaTradeService());
  }

  @Test
  void classicGettersFailActionablyInUtaMode() {
    KucoinExchange exchange = exchangeWith(KucoinApiMode.UTA);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, exchange::getMarketDataService);
    org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("getUtaMarketDataService"));
  }

  @Test
  void utaGettersFailActionablyInClassicMode() {
    KucoinExchange exchange = exchangeWith(null);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, exchange::getUtaTradeService);
    org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("apiMode"));
  }

  @Test
  void invalidModeFailsEarlyAtSpecificationTime() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> exchangeWith("FUTURES"));
    org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("CLASSIC or UTA"));
  }
}
