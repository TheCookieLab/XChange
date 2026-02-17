package org.knowm.xchange.kraken;

import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.dto.meta.ExchangeHealth;

public class KrakenIntegrationTestParent {

  protected static KrakenExchange exchange;

  @BeforeAll
  public static void init() {
    if (exchange == null) {
      exchange = ExchangeFactory.INSTANCE.createExchange(KrakenExchange.class);
    }
  }

  @BeforeEach
  public void exchange_online() {
    try {
      assumeThat(exchange.getMarketDataService().getExchangeHealth()).isEqualTo(ExchangeHealth.ONLINE);
    } catch (RuntimeException e) {
      assumeTrue(false, "Unable to determine Kraken health: " + e.getMessage());
    }
  }
}
