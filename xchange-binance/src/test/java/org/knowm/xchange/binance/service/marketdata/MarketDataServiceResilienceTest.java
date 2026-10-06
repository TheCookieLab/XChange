package org.knowm.xchange.binance.service.marketdata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import org.junit.Test;
import org.knowm.xchange.binance.AbstractResilienceTest;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;

public class MarketDataServiceResilienceTest extends AbstractResilienceTest {

  @Test
  public void shouldSucceedIfFirstCallTimeoutedAndRetryIsEnabled() throws Exception {
    BinanceAdapters.putSymbolMapping("BNBBTC", new CurrencyPair("BNB/BTC"));
    // given
    MarketDataService service = createExchangeWithRetryEnabled().getMarketDataService();
    stubForTicker24WithFirstCallTimetoutAndSecondSuccessful();
    Instrument instrument = new CurrencyPair("BNB/BTC");
    // when
    Ticker ticker = service.getTicker(instrument);

    // then
    assertThat(ticker.getLast()).isEqualByComparingTo("4.00000200");
  }

  @Test
  public void shouldFailIfFirstCallTimeoutedAndRetryIsDisabled() throws Exception {
    // given
    MarketDataService service = createExchangeWithRetryDisabled().getMarketDataService();
    stubForTicker24WithFirstCallTimetoutAndSecondSuccessful();
    Instrument instrument = new CurrencyPair("BNB/BTC");
    // when
    Throwable exception = catchThrowable(() -> service.getTicker(instrument));

    // then
    assertThat(exception).isInstanceOf(IOException.class);
  }

  private void stubForTicker24WithFirstCallTimetoutAndSecondSuccessful() {
    stubFor(
        get(urlPathEqualTo("/api/v3/ticker/24hr"))
            .inScenario("Retry read")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withFixedDelay(READ_TIMEOUT_MS * 2).withStatus(500))
            .willSetStateTo("After fail"));
    stubFor(
        get(urlPathEqualTo("/api/v3/ticker/24hr"))
            .inScenario("Retry read")
            .whenScenarioStateIs("After fail")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("single-24hr-ticker.json")));
  }

  private void stubForDepth() {
    stubFor(
        get(urlPathEqualTo("/api/v3/depth"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("depth.json")));
  }
}
