package org.knowm.xchange.bitget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.config.BitgetApiMode;

class BitgetExchangeTest {

  /**
   * {@link org.knowm.xchange.BaseExchange#applySpecification(org.knowm.xchange.ExchangeSpecification)}
   * documents {@code null} as "use the default specification"; Bitget's mode-aware override must
   * not break that contract for the default (classic) mode.
   */
  @Test
  void applySpecificationNullFallsBackToDefaultClassicMode() {
    BitgetExchange exchange = new BitgetExchange();
    exchange.applySpecification(null);

    assertThat(exchange.getApiMode()).isEqualTo(BitgetApiMode.CLASSIC_V2);
    assertThat(exchange.getAccountService()).isNotNull();
    assertThat(exchange.getMarketDataService()).isNotNull();
    assertThat(exchange.getTradeService()).isNotNull();
  }
}
