package org.knowm.xchange.kraken.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kraken.KrakenExchangeWiremock;
import org.knowm.xchange.kraken.dto.trade.results.KrakenTradeHistoryResult.KrakenTradeHistory;

/** Bounded cursor pagination and modern flags for the TradesHistory endpoint. */
public class KrakenTradeHistoryPaginationTest extends KrakenExchangeWiremock {

  private static final String PATH = "/0/private/TradesHistory";

  private KrakenTradeServiceRaw raw;

  @BeforeAll
  public static void configureWireMockClient() {
    com.github.tomakehurst.wiremock.client.WireMock.configureFor(
        "localhost", wireMockServer.port());
  }

  @BeforeEach
  public void setUp() {
    com.github.tomakehurst.wiremock.client.WireMock.reset();
    raw = (KrakenTradeServiceRaw) exchange.getTradeService();
  }

  private void stubPage(String fileName) {
    stubFor(
        post(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile(fileName)));
  }

  private void stubPage(String fileName, String bodyFragment) {
    stubFor(
        post(urlEqualTo(PATH))
            .withRequestBody(containing(bodyFragment))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile(fileName)));
  }

  @Test
  void full_fetch_stops_when_provider_count_reached() throws IOException {
    // single page already contains the whole window: exactly one request
    stubPage("0_private_tradeshistory-784b2389-ad84-4c35-a203-be9de5c8b0c5.json");
    // fallback page for the offset cursor (not reached)
    stubPage("0_private_tradeshistory-empty.json", "ofs=4");

    KrakenTradeHistory history = raw.getKrakenTradeHistoryAll(null, false, null, null, null);

    assertThat(history.getCount()).isEqualTo(4);
    assertThat(history.getTrades()).hasSize(4);
    assertThat(history.getTrades())
        .containsKeys(
            "T7FJQW-EBPSX-R5RK3J",
            "TAGKDK-PVBGJ-RZ4N35",
            "TO5YFV-E6NHJ-3OA7IH",
            "TSVZ66-RESHM-IDZGCE");
    verify(1, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  void full_fetch_follows_offset_pages_until_count() throws IOException {
    stubPage("0_private_tradeshistory-page1.json");
    stubPage("0_private_tradeshistory-page2.json", "ofs=2");

    KrakenTradeHistory history = raw.getKrakenTradeHistoryAll(null, false, null, null, null);

    assertThat(history.getTrades()).hasSize(4);
    assertThat(history.getTrades())
        .containsKeys(
            "T7FJQW-EBPSX-R5RK3J",
            "TAGKDK-PVBGJ-RZ4N35",
            "TO5YFV-E6NHJ-3OA7IH",
            "TSVZ66-RESHM-IDZGCE");
    verify(2, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  void full_fetch_stops_at_empty_page() throws IOException {
    stubPage("0_private_tradeshistory-page1.json");
    stubPage("0_private_tradeshistory-empty.json", "ofs=2");

    KrakenTradeHistory history = raw.getKrakenTradeHistoryAll(null, false, null, null, null);

    // provider count claims 4 but the next page is empty: stop with what was collected
    assertThat(history.getTrades()).hasSize(2);
    verify(2, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  void full_fetch_throws_on_repeated_page_without_progress() {
    stubPage("0_private_tradeshistory-page1.json");

    assertThatExceptionOfType(ExchangeException.class)
        .isThrownBy(() -> raw.getKrakenTradeHistoryAll(null, false, null, null, null))
        .withMessageContaining("no progress");
  }

  @Test
  void consolidate_and_include_trades_flags_are_sent() throws IOException {
    stubPage("0_private_tradeshistory-page1.json");
    stubPage("0_private_tradeshistory-empty.json", "ofs=2");

    KrakenTradeHistoryParams params =
        KrakenTradeHistoryParams.builder()
            .currencyPair(CurrencyPair.BTC_USDT)
            .includeTrades(true)
            .consolidateTrades(true)
            .build();
    exchange.getTradeService().getTradeHistory(params);

    verify(
        postRequestedFor(urlEqualTo(PATH))
            .withRequestBody(containing("trades=true"))
            .withRequestBody(containing("consolidate_trades=true")));
  }

  @Test
  void explicit_offset_keeps_single_page() throws IOException {
    stubPage("0_private_tradeshistory-page2.json");

    KrakenTradeHistoryParams params =
        KrakenTradeHistoryParams.builder().currencyPair(CurrencyPair.ETH_USDT).offset(2L).build();
    var userTrades = exchange.getTradeService().getTradeHistory(params).getUserTrades();
    assertThat(userTrades).hasSize(2);
    assertThat(userTrades)
        .extracting(t -> t.getId())
        .containsExactlyInAnyOrder("TO5YFV-E6NHJ-3OA7IH", "TSVZ66-RESHM-IDZGCE");

    verify(1, postRequestedFor(urlEqualTo(PATH)));
  }
}
