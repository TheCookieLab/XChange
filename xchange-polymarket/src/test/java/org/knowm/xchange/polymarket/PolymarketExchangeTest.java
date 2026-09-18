package org.knowm.xchange.polymarket;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.prediction.PredictionMarketContract;

/** {@code remoteInit} must page the Gamma keyset catalog and register one contract per token. */
class PolymarketExchangeTest {

  private static final String PAGE_TWO_CURSOR = "page-2";

  @TempDir Path tempDir;

  private WireMockServer server;
  private PolymarketExchange exchange;

  @BeforeEach
  void setUp() {
    PolymarketAdapters.resetNegRiskRegistry();
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
  void remoteInitRegistersOutcomeTokensAcrossKeysetPages() throws Exception {
    // A full first page (exactly the page size) forces a second request.
    stubFirstKeysetPage(
        keysetPage(
            PAGE_TWO_CURSOR,
            Collections.nCopies(100, marketJson("0xaaa", "t1", "t2", false)).toArray(String[]::new)));
    stubKeysetPage(
        PAGE_TWO_CURSOR,
        keysetPage(
            "",
            marketJson("0xbbb", "t3", "t4", false),
            marketJson("0xccc", "t5", "t6", true)));

    exchange.remoteInit();

    var instruments = exchange.getExchangeMetaData().getInstruments();
    for (Instrument instrument : instruments.keySet()) {
      assertInstanceOf(PredictionMarketContract.class, instrument);
    }
    assertEquals(4, instruments.size(), "two tokens each for 0xaaa and 0xbbb; closed excluded");
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xaaa", "t1", Currency.PUSD)));
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xaaa", "t2", Currency.PUSD)));
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xbbb", "t4", Currency.PUSD)));
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xbbb", "t3", Currency.PUSD)));

    var metadata =
        instruments.get(
            new PredictionMarketContract("polymarket", null, "0xaaa", "t1", Currency.PUSD));
    assertEquals(4, metadata.getPriceScale());
    assertEquals(new BigDecimal("0.001"), metadata.getPriceStepSize());
    assertEquals(new BigDecimal("5"), metadata.getMinimumAmount());
    assertEquals(Currency.PUSD, metadata.getTradingFeeCurrency());
    assertTrue(exchange.getExchangeMetaData().getCurrencies().containsKey(Currency.PUSD));
    assertEquals(Boolean.FALSE, PolymarketAdapters.negRiskForCondition("0xaaa"));
    assertEquals(2, server.getAllServeEvents().size(), "paging must stop at the blank cursor");
    assertTrue(
        server.getAllServeEvents().stream()
            .allMatch(event -> event.getRequest().getUrl().startsWith("/markets/keyset")),
        "the deprecated offset endpoint must not be used");
  }

  @Test
  void remoteInitHonoursConfiguredPageBound() throws Exception {
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(PolymarketExchange.PARAM_GAMMA_DISCOVERY_PAGES, 1);
    stubFirstKeysetPage(
        keysetPage(PAGE_TWO_CURSOR, marketJson("0xaaa", "t1", "t2", false)));

    exchange.remoteInit();

    assertEquals(2, exchange.getExchangeMetaData().getInstruments().size());
    assertEquals(1, server.getAllServeEvents().size(), "the page bound must stop the walk");
  }

  @Test
  void remoteInitResumesFromCachedCatalog() throws Exception {
    Path cache = tempDir.resolve("gamma-catalog.jsonl");
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(
            PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE, cache.toString());
    stubFirstKeysetPage(
        keysetPage(
            PAGE_TWO_CURSOR, Collections.nCopies(100, marketJson("0xaaa", "t1", "t2", false))
                .toArray(String[]::new)));
    stubKeysetPage(
        PAGE_TWO_CURSOR,
        keysetPage(
            "",
            marketJson("0xbbb", "t3", "t4", false),
            marketJson("0xccc", "t5", "t6", true)));

    exchange.remoteInit();
    assertEquals(4, exchange.getExchangeMetaData().getInstruments().size());
    assertEquals(2, server.getAllServeEvents().size(), "the first walk reads both pages");
    assertTrue(Files.exists(cache), "the walk must persist the catalog");

    exchange.remoteInit();

    assertEquals(
        4,
        exchange.getExchangeMetaData().getInstruments().size(),
        "the resumed walk must still register the cached markets");
    assertEquals(
        3,
        server.getAllServeEvents().size(),
        "a fresh cache resumes at the stored cursor instead of re-reading the catalog");
  }

  @Test
  void remoteInitReplacesTheCachedCatalogOnAFullSweep() throws Exception {
    Path cache = tempDir.resolve("gamma-catalog.jsonl");
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(
            PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE, cache.toString());
    // TTL 0: every run sweeps from the start rather than resuming.
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(
            PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE_TTL, 0);
    stubFirstKeysetPage(keysetPage(PAGE_TWO_CURSOR, marketJson("0xaaa", "t1", "t2", false)));
    stubKeysetPage(PAGE_TWO_CURSOR, keysetPage("", marketJson("0xbbb", "t3", "t4", false)));

    exchange.remoteInit();
    assertEquals(4, exchange.getExchangeMetaData().getInstruments().size());

    // The provider no longer returns 0xaaa: it closed or deactivated, so a from-the-start sweep
    // must drop its cached row instead of republishing a stale contract forever.
    stubFirstKeysetPage(keysetPage("", marketJson("0xddd", "t7", "t8", false)));
    exchange.remoteInit();

    var instruments = exchange.getExchangeMetaData().getInstruments();
    assertEquals(2, instruments.size(), "a full sweep must replace the cached catalog");
    assertTrue(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xddd", "t7", Currency.PUSD)));
    assertFalse(
        instruments.containsKey(
            new PredictionMarketContract("polymarket", null, "0xaaa", "t1", Currency.PUSD)),
        "a row the provider stopped returning must not survive the sweep");
  }

  @Test
  void remoteInitFailsWhenTheKeysetCursorDoesNotAdvance() {
    // A bound keeps a regression from hanging this test; the guard under test is the same code
    // path as the unbounded default walk.
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(PolymarketExchange.PARAM_GAMMA_DISCOVERY_PAGES, 3);
    stubFirstKeysetPage(keysetPage("stuck-cursor", marketJson("0xaaa", "t1", "t2", false)));
    stubKeysetPage(
        "stuck-cursor", keysetPage("stuck-cursor", marketJson("0xaaa", "t1", "t2", false)));

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> exchange.remoteInit());

    assertTrue(error.getMessage().contains("did not advance"), error.getMessage());
    assertEquals(
        2, server.getAllServeEvents().size(), "a repeated cursor must stop the walk, not loop");
  }

  @Test
  void remoteInitSweepsAgainWhenCacheExpires() throws Exception {
    Path cache = tempDir.resolve("gamma-catalog.jsonl");
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(
            PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE, cache.toString());
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(
            PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE_TTL, 0);
    stubFirstKeysetPage(
        keysetPage(
            PAGE_TWO_CURSOR, Collections.nCopies(100, marketJson("0xaaa", "t1", "t2", false))
                .toArray(String[]::new)));
    stubKeysetPage(
        PAGE_TWO_CURSOR,
        keysetPage(
            "",
            marketJson("0xbbb", "t3", "t4", false),
            marketJson("0xccc", "t5", "t6", true)));

    exchange.remoteInit();
    exchange.remoteInit();

    assertEquals(4, exchange.getExchangeMetaData().getInstruments().size());
    assertEquals(
        4,
        server.getAllServeEvents().size(),
        "an expired cache must sweep the catalog from the start again");
  }

  @Test
  void remoteInitRecoversFromRejectedCursor() throws Exception {
    Path cache = tempDir.resolve("gamma-catalog.jsonl");
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem(
            PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE, cache.toString());
    Files.writeString(
        cache,
        "{\"version\":1,\"gammaUri\":\""
            + server.baseUrl()
            + "\",\"closed\":false,\"cursor\":\"stale-cursor\",\"fullWalkAt\":"
            + System.currentTimeMillis()
            + "}"
            + System.lineSeparator(),
        StandardCharsets.UTF_8);
    stubFirstKeysetPage(
        keysetPage(
            PAGE_TWO_CURSOR, Collections.nCopies(100, marketJson("0xaaa", "t1", "t2", false))
                .toArray(String[]::new)));
    stubKeysetPage(
        PAGE_TWO_CURSOR,
        keysetPage(
            "",
            marketJson("0xbbb", "t3", "t4", false),
            marketJson("0xccc", "t5", "t6", true)));
    server.stubFor(
        get(urlPathEqualTo("/markets/keyset"))
            .withQueryParam("after_cursor", equalTo("stale-cursor"))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Connection", "close")
                    .withBody("{\"type\":\"validation error\",\"error\":\"invalid cursor\"}")));

    exchange.remoteInit();

    assertEquals(
        4,
        exchange.getExchangeMetaData().getInstruments().size(),
        "a rejected cursor must fall back to a from-the-start sweep");
    assertEquals(
        3,
        server.getAllServeEvents().size(),
        "one rejected resume, then both catalog pages");
  }

  private void stubFirstKeysetPage(String body) {
    server.stubFor(
        get(urlPathEqualTo("/markets/keyset"))
            .withQueryParam("limit", equalTo("100"))
            .withQueryParam("closed", equalTo("false"))
            .withQueryParam("after_cursor", absent())
            // The keyset request carries no body, so it must declare no content type.
            .withHeader("Content-Type", absent())
            .willReturn(keysetResponse(body)));
  }

  private void stubKeysetPage(String cursor, String body) {
    server.stubFor(
        get(urlPathEqualTo("/markets/keyset"))
            .withQueryParam("after_cursor", equalTo(cursor))
            .willReturn(keysetResponse(body)));
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
      keysetResponse(String body) {
    return aResponse()
        .withHeader("Content-Type", "application/json")
        // Connection: close keeps the rescu client from pooling a connection that Jetty may
        // idle-close between paginated requests (sporadic NoHttpResponseException "Unexpected end
        // of file from server").
        .withHeader("Connection", "close")
        .withBody(body);
  }

  private static String keysetPage(String nextCursor, String... markets) {
    return "{\"markets\":["
        + String.join(",", markets)
        + "],\"next_cursor\":\""
        + nextCursor
        + "\"}";
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
        + "\"volume\":\"1000\",\"negRisk\":false}";
  }
}
