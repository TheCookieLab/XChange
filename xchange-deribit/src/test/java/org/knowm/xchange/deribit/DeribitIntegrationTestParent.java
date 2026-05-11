package org.knowm.xchange.deribit;

import static org.assertj.core.api.Assumptions.assumeThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.deribit.v2.DeribitExchange;
import org.knowm.xchange.dto.meta.ExchangeHealth;

public class DeribitIntegrationTestParent {

  protected static DeribitExchange exchange;

  @BeforeAll
  static void init() {
    if (exchange == null) {
      exchange = ExchangeFactory.INSTANCE.createExchange(DeribitExchange.class);
    }
  }

  @BeforeEach
  void exchange_online() {
    // skip if offline
    assumeExchangeOnline(exchange);
  }

  /**
   * Creates a Deribit sandbox exchange without remote metadata loading.
   *
   * <p>Sandbox integration tests still verify live API calls, but setup must not fail before JUnit
   * assumptions can skip the test when Deribit's sandbox gateway is temporarily unavailable.
   *
   * @return a configured Deribit sandbox exchange whose services are ready for public API calls.
   */
  public static DeribitExchange createSandboxExchangeWithoutRemoteMetadata() {
    DeribitExchange sandboxExchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(DeribitExchange.class);
    ExchangeSpecification specification = sandboxExchange.getSandboxExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    sandboxExchange.applySpecification(specification);
    return sandboxExchange;
  }

  /**
   * Skips the current integration test when Deribit's API reports an unhealthy state.
   *
   * @param deribitExchange exchange instance whose market-data health endpoint should be checked.
   */
  public static void assumeExchangeOnline(DeribitExchange deribitExchange) {
    assumeThat(deribitExchange.getMarketDataService().getExchangeHealth())
        .isEqualTo(ExchangeHealth.ONLINE);
  }
}
