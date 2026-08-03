package org.knowm.xchange.polymarket;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.prediction.PredictionMarketContract;

/** {@code remoteInit} must page Gamma to completion and register one contract per outcome token. */
class PolymarketExchangeTest {

  private WireMockServer server;
  private PolymarketExchange exchange;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    exchange = new PolymarketExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setSslUri(server.baseUrl());
    spec.setExchangeSpecificParametersItem(PolymarketExchange.PARAM_GAMMA_URI, server.baseUrl());
    spec.setExchangeSpecificParametersItem(PolymarketExchange.PARAM_DATA_URI, server.baseUrl());
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void remoteInitRegistersOutcomeTokensAcrossOffsetPages() throws Exception {
    // A full first page (exactly the page size) forces a second request.
    String page1 =
        "[" + String.join(",", Collections.nCopies(100, marketJson("0xaaa", "t1", "t2", false)))
            + "]";
    String page2 =
        "["
            + marketJson("0xbbb", "t3", "t4", false)
            + ","
            + marketJson("0xccc", "t5", "t6", true)
            + "]";
    server.stubFor(
        get(urlPathEqualTo("/markets"))
            .withQueryParam("offset", equalTo("0"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(page1)));
    server.stubFor(
        get(urlPathEqualTo("/markets"))
            .withQueryParam("offset", equalTo("100"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(page2)));

    exchange.remoteInit();

    var instruments = exchange.getExchangeMetaData().getInstruments();
    for (Instrument instrument : instruments.keySet()) {
      assertInstanceOf(PredictionMarketContract.class, instrument);
    }
    assertEquals(4, instruments.size(), "two tokens each for 0xaaa and 0xbbb; closed excluded");
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xaaa", "t1", Currency.USD)));
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xaaa", "t2", Currency.USD)));
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xbbb", "t4", Currency.USD)));

    var metadata =
        instruments.get(new PredictionMarketContract("polymarket", null, "0xaaa", "t1",
            Currency.USD));
    assertEquals(4, metadata.getPriceScale());
    assertEquals(new BigDecimal("0.001"), metadata.getPriceStepSize());
    assertEquals(new BigDecimal("5"), metadata.getMinimumAmount());
    assertTrue(exchange.getExchangeMetaData().getCurrencies().containsKey(Currency.USD));
    assertEquals(2, server.getAllServeEvents().size(), "paging must stop at the short page");
  }

  private static String marketJson(
      String conditionId, String tokenA, String tokenB, boolean closed) {
    return "{\"id\":\"id-"
        + conditionId
        + "\",\"conditionId\":\""
        + conditionId
        + "\",\"question\":\"q?\",\"outcomes\":\"[\\\"Yes\\\",\\\"No\\\"]\","
        + "\"outcomePrices\":\"[\\\"0.5\\\",\\\"0.5\\\"]\",\"clobTokenIds\":\"[\\\""
        + tokenA
        + "\\\",\\\""
        + tokenB
        + "\\\"]\",\"active\":true,\"closed\":"
        + closed
        + ",\"enableOrderBook\":true,\"orderMinSize\":5,\"orderPriceMinTickSize\":0.001,"
        + "\"volume\":\"1000\"}";
  }
}
