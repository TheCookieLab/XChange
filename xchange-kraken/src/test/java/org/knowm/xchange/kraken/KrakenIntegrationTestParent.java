package org.knowm.xchange.kraken;

import org.junit.Before;
import org.junit.BeforeClass;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.dto.meta.ExchangeHealth;

import static org.assertj.core.api.Assumptions.assumeThat;

public class KrakenIntegrationTestParent {

  protected static KrakenExchange exchange;

  @BeforeClass
  public static void init() {
    if (exchange == null) {
      exchange = ExchangeFactory.INSTANCE.createExchange(KrakenExchange.class);
    }
  }

  @Before
  public void exchange_online() {
    // skip if offline
    assumeThat(exchange.getMarketDataService().getExchangeHealth())
        .isEqualTo(ExchangeHealth.ONLINE);
  }
}
