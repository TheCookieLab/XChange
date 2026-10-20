package org.knowm.xchange.coinbase.cde;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.Test;
import si.mazi.rescu.RestProxyFactory;

/** Offline contract tests; the local server verifies the actual public wire request. */
public class CoinbaseCdeMarketDataTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void institutionalHistoryUsesSeparateRouteAndRetainsDocumentedFields() throws Exception {
    WireMockServer server = new WireMockServer(options().dynamicPort());
    server.start();
    try {
      server.stubFor(get(urlPathEqualTo("/rest/funding-rate"))
          .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
              [{"symbol":"BIPZ30","funding_rate":"0.000100",
                "future_mark_price":"62850.25","spot_mark_price":"62840.10",
                "fair_value_price":"62845.00","event_time":"2025-08-13T12:00:00Z"}]
              """)));
      CoinbaseCdeRest client = RestProxyFactory.createProxy(CoinbaseCdeRest.class, server.baseUrl());
      // Synthetic headers only: no signature generation or live/signed account request.
      var rows = client.getHistoricalFundingRates(
          "synthetic-key", "synthetic-passphrase", "synthetic-signature", 1L,
          "BIPZ30", "2025-08-01");
      assertEquals(1, rows.size());
      assertEquals("BIPZ30", rows.get(0).symbol());
      assertEquals("0.000100", rows.get(0).fundingRate());
      assertEquals("62850.25", rows.get(0).futureMarkPrice());
      assertEquals("62840.10", rows.get(0).spotMarkPrice());
      assertEquals("62845.00", rows.get(0).fairValuePrice());
      assertEquals("2025-08-13T12:00:00Z", rows.get(0).eventTime());
      var request = server.getAllServeEvents().get(0).getRequest();
      assertEquals("BIPZ30", request.queryParameter("symbol").firstValue());
      assertEquals("2025-08-01", request.queryParameter("trading_session_date").firstValue());
      assertEquals("synthetic-key", request.getHeader("CB-ACCESS-KEY"));
      assertEquals("synthetic-passphrase", request.getHeader("CB-ACCESS-PASSPHRASE"));
      assertEquals("synthetic-signature", request.getHeader("CB-ACCESS-SIGN"));
      assertEquals("1", request.getHeader("CB-ACCESS-TIMESTAMP"));
      assertFalse(request.containsHeader("Authorization"));
      client.getHistoricalFundingRates("synthetic-key", "synthetic-passphrase",
          "synthetic-signature", 1L, "BIPZ30", null);
      assertFalse(server.getAllServeEvents().get(0).getRequest()
          .queryParameter("trading_session_date").isPresent());
      // There is no supplied credential-bearing default transport; stock Rescu TRACE logs headers.
      assertThrows(NoSuchMethodException.class, () -> CoinbaseCdeRest.class.getMethod("create"));
    } finally {
      server.stop();
    }
  }

  @Test
  public void publicPageUsesCdeSymbolOffsetAndNoAccountHeaders() throws Exception {
    WireMockServer server = new WireMockServer(options().dynamicPort());
    server.start();
    try {
      server.stubFor(
          get(urlPathEqualTo("/api/mda/v1/instruments/CDE/BIPZ30/funding"))
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {"pagination":{"result_limit":2,"result_offset":2160},"results":[
                            {"symbol":"BIPZ30","funding_rate":"0.0000090",
                             "event_time":"2026-07-22T13:00:00Z"},
                            {"symbol":"BIPZ30","funding_rate":"-0.0000012",
                             "event_time":"2026-07-22T11:00:00Z"}]}
                          """)));
      CoinbaseCdeMarketData client =
          RestProxyFactory.createProxy(CoinbaseCdeMarketData.class, server.baseUrl());

      CoinbaseCdeFundingPage page = client.getFundingHistory("BIPZ30", 2, 2160);

      assertEquals(Integer.valueOf(2160), page.pagination().resultOffset());
      assertEquals(Integer.valueOf(2), page.pagination().resultLimit());
      assertEquals(2, page.results().size());
      assertEquals("BIPZ30", page.results().get(0).symbol());
      assertEquals("0.0000090", page.results().get(0).fundingRate());
      assertEquals(new BigDecimal("0.0000090"), page.results().get(0).fundingRateDecimal());
      assertEquals(new BigDecimal("-0.0000012"), page.results().get(1).fundingRateDecimal());
      assertEquals(Instant.parse("2026-07-22T13:00:00Z"),
          page.results().get(0).eventTimeInstant());
      // The missing 12:00 observation is neither inserted nor converted to zero.
      assertEquals("2026-07-22T11:00:00Z", page.results().get(1).eventTime());
      var request = server.getAllServeEvents().get(0).getRequest();
      assertEquals("2", request.queryParameter("result_limit").firstValue());
      assertEquals("2160", request.queryParameter("result_offset").firstValue());
      assertFalse(request.containsHeader("Authorization"));
      assertFalse(request.containsHeader("CB-ACCESS-KEY"));
      assertFalse(request.containsHeader("CB-ACCESS-SIGN"));
      assertFalse(request.containsHeader("Cookie"));
    } finally {
      server.stop();
    }
  }

  @Test
  public void ignoresExtraFieldsAndPreservesEmptyPagesAndMissingMetadataWithoutInventingCoverage()
      throws Exception {
    CoinbaseCdeFundingPage empty = MAPPER.readValue(
        "{\"pagination\":{\"result_limit\":2,\"result_offset\":5000,\"total\":7},"
            + "\"results\":[],\"extra\":true}", CoinbaseCdeFundingPage.class);
    assertTrue(empty.results().isEmpty());
    assertEquals(Integer.valueOf(5000), empty.pagination().resultOffset());
    CoinbaseCdeFundingPage missing = MAPPER.readValue("{}", CoinbaseCdeFundingPage.class);
    assertEquals(null, missing.results());
    assertEquals(null, missing.pagination());
  }

  @Test
  public void conversionKeepsDecimalUnitsAndRejectsMalformedObservations() throws Exception {
    CoinbaseCdeFundingPage page = MAPPER.readValue(
        """
        {"results":[{"symbol":"ETPZ30","funding_rate":"0.000022",
        "event_time":"2026-10-20T13:00:00.000Z","extra":true},
        {"symbol":"ETPZ30","funding_rate":"0.0","event_time":"invalid"}]}
        """, CoinbaseCdeFundingPage.class);
    assertEquals("ETPZ30", page.results().get(0).symbol());
    assertEquals(new BigDecimal("0.000022"), page.results().get(0).fundingRateDecimal());
    assertEquals("2026-10-20T13:00:00.000Z", page.results().get(0).eventTime());
    assertEquals(new BigDecimal("0.0"), page.results().get(1).fundingRateDecimal());
    assertThrows(java.time.format.DateTimeParseException.class,
        () -> page.results().get(1).eventTimeInstant());
    assertThrows(NumberFormatException.class,
        () -> new CoinbaseCdeFundingPage.Observation("BIPZ30", "not-a-rate", "invalid")
            .fundingRateDecimal());
    assertNotNull(CoinbaseCdeMarketData.create()); // Construction performs no network request.
  }
}
