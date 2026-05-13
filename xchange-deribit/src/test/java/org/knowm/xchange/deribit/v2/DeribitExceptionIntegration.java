package org.knowm.xchange.deribit.v2;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.deribit.DeribitIntegrationTestParent;
import org.knowm.xchange.deribit.v2.dto.DeribitException;
import org.knowm.xchange.deribit.v2.service.DeribitMarketDataService;
import org.knowm.xchange.exceptions.CurrencyPairNotValidException;
import org.knowm.xchange.instrument.Instrument;

public class DeribitExceptionIntegration {
  private static DeribitExchange exchange;
  private static DeribitMarketDataService deribitMarketDataService;

  @BeforeAll
  public static void setUp() {
    exchange = DeribitIntegrationTestParent.createSandboxExchangeWithoutRemoteMetadata();
    deribitMarketDataService = (DeribitMarketDataService) exchange.getMarketDataService();
  }

  @BeforeEach
  void exchangeOnline() {
    DeribitIntegrationTestParent.assumeExchangeOnline(exchange);
  }

  @Test
  public void getTickerThrowsExceptionTest() throws Exception {
    Instrument pair = new CurrencyPair("?", "?");
    assertThatExceptionOfType(CurrencyPairNotValidException.class)
        .isThrownBy(() -> deribitMarketDataService.getTicker(pair));
  }

  @Test
  public void getDeribitTickerThrowsExceptionTest() throws Exception {
    assertThatExceptionOfType(DeribitException.class)
        .isThrownBy(() -> deribitMarketDataService.getDeribitTicker("?"));
  }

  @Test
  public void getDeribitInstrumentsThrowsIllegalArgumentExceptionTest() throws Exception {
    assertThatExceptionOfType(DeribitException.class)
        .isThrownBy(
            () -> deribitMarketDataService.getDeribitInstruments("BTC-PERPETUAAAAL", null, null));
  }
}
