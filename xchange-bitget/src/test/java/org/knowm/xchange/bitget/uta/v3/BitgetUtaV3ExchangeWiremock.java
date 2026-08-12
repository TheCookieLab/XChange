package org.knowm.xchange.bitget.uta.v3;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.config.BitgetApiMode;
import org.knowm.xchange.bitget.config.BitgetConfiguration;

/**
 * Base for UTA v3 unit tests: starts a dynamic-port WireMock server and builds a {@link
 * BitgetExchange} in {@link BitgetApiMode#UTA_V3}.
 *
 * <p>The instruments endpoint is stubbed for all four futures/spot categories so that {@code
 * remoteInit()} (run by {@link ExchangeFactory}) can build metadata; each test stubs the specific
 * endpoints it exercises.
 */
public abstract class BitgetUtaV3ExchangeWiremock {

  protected static WireMockServer wireMockServer;
  protected static BitgetExchange exchange;

  @BeforeAll
  public static void initExchange() {
    wireMockServer = new WireMockServer(options().dynamicPort());
    wireMockServer.start();

    stubInstruments();

    ExchangeSpecification exSpec = new ExchangeSpecification(BitgetExchange.class);
    exSpec.setSslUri("http://localhost:" + wireMockServer.port());
    exSpec.setApiKey("a");
    exSpec.setSecretKey("b");
    exSpec.setPassword("c");
    exSpec.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);

    exchange = (BitgetExchange) ExchangeFactory.INSTANCE.createExchange(exSpec);
  }

  private static void stubInstruments() {
    for (String category : new String[] {"spot", "usdt-futures", "coin-futures", "usdc-futures"}) {
      String symbol = "spot".equals(category) ? "BTCUSDT" : "BTCUSDT";
      wireMockServer.stubFor(
          get(urlPathEqualTo("/api/v3/market/instruments"))
              .withQueryParam(
                  "category", com.github.tomakehurst.wiremock.client.WireMock.equalTo(category))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                              + "\"data\":[{\"symbol\":\""
                              + symbol
                              + "\",\"baseCoin\":\"BTC\",\"quoteCoin\":\"USDT\",\"minTradeNum\":\"0.0001\","
                              + "\"maxTradeNum\":\"10\",\"pricePrecision\":\"2\",\"quantityPrecision\":\"4\","
                              + "\"status\":\"online\",\"isReality\":\"no\",\"category\":\""
                              + category
                              + "\"}]}")));
    }
  }

  /**
   * Each test starts from a clean stub slate (plus the instruments endpoints) so that cursor-scoped
   * stubs registered by one test never intercept another test's requests.
   */
  @BeforeEach
  public void resetStubs() {
    wireMockServer.resetAll();
    stubInstruments();
  }

  @AfterAll
  public static void stop() {
    wireMockServer.stop();
  }
}
