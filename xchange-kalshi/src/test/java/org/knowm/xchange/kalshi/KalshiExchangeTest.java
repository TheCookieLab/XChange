package org.knowm.xchange.kalshi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.prediction.PredictionMarketContract;

/** {@code remoteInit} must register only real provider markets as prediction contracts. */
class KalshiExchangeTest {

  private WireMockServer server;
  private KalshiExchange exchange;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    exchange = new KalshiExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setSslUri(server.baseUrl());
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void remoteInitRegistersOpenMarketsAcrossCursorPages() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/markets"))
            .withQueryParam("cursor", absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"markets\":["
                            + marketJson("KXBTC-25DEC31-T90000", "KXBTC-25DEC31", "active")
                            + ","
                            + marketJson("KXETH-25DEC31-T4000", "KXETH-25DEC31", "closed")
                            + "],\"cursor\":\"page-2\"}")));
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/markets"))
            .withQueryParam("cursor", equalTo("page-2"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"markets\":["
                            + marketJson("KXFED-26JAN28-H25", "KXFED-26JAN28", "active")
                            + "],\"cursor\":\"\"}")));

    exchange.remoteInit();

    var instruments = exchange.getExchangeMetaData().getInstruments();
    assertEquals(2, instruments.size());
    for (Instrument instrument : instruments.keySet()) {
      assertInstanceOf(PredictionMarketContract.class, instrument);
    }
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract(
                "kalshi", "KXBTC-25DEC31", "KXBTC-25DEC31-T90000", "YES", Currency.USD)));
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract(
                "kalshi", "KXFED-26JAN28", "KXFED-26JAN28-H25", "YES", Currency.USD)));
    assertTrue(exchange.getExchangeMetaData().getCurrencies().containsKey(Currency.USD));
  }

  private static String marketJson(String ticker, String eventTicker, String status) {
    return "{\"ticker\":\""
        + ticker
        + "\",\"event_ticker\":\""
        + eventTicker
        + "\",\"title\":\"t\",\"status\":\""
        + status
        + "\",\"yes_bid_dollars\":\"0.5300\",\"yes_ask_dollars\":\"0.5400\","
        + "\"last_price_dollars\":\"0.5200\",\"volume_fp\":\"1000.00\","
        + "\"open_interest_fp\":\"500.00\",\"notional_value_dollars\":\"1.0000\","
        + "\"price_ranges\":[{\"start\":\"0.0000\",\"end\":\"1.0000\",\"step\":\"0.0100\"}],"
        + "\"future_field\":\"tolerated\"}";
  }
}
