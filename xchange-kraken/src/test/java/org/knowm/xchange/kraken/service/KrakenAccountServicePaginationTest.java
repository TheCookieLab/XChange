package org.knowm.xchange.kraken.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kraken.KrakenExchangeWiremock;
import org.knowm.xchange.kraken.dto.account.KrakenLedger;

/** Bounded pagination behavior of the ledger history iteration. */
public class KrakenAccountServicePaginationTest extends KrakenExchangeWiremock {

  @BeforeAll
  public static void configureWireMockClient() {
    com.github.tomakehurst.wiremock.client.WireMock.configureFor(
        "localhost", wireMockServer.port());
  }

  private KrakenAccountServiceRaw rawAccountService() {
    return (KrakenAccountServiceRaw) exchange.getAccountService();
  }

  private void stubLedgerPage(String bodyFileName, boolean withOffset) {
    MappingBuilder builder = post(urlEqualTo("/0/private/Ledgers"));
    if (withOffset) {
      builder = builder.withRequestBody(matching(".*ofs=.*"));
    } else {
      builder = builder.withRequestBody(notMatching(".*ofs=.*"));
    }
    stubFor(
        builder.willReturn(
            aResponse()
                .withStatus(200)
                .withBodyFile("0_private_ledgers-b11b7863-ff7e-4602-914a-d97b9efccc27.json")));
  }

  private void stubEmptyLedgerPage(boolean withOffset) {
    MappingBuilder builder = post(urlEqualTo("/0/private/Ledgers"));
    if (withOffset) {
      builder = builder.withRequestBody(matching(".*ofs=.*"));
    } else {
      builder = builder.withRequestBody(notMatching(".*ofs=.*"));
    }
    stubFor(
        builder.willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":[],\"result\":{\"count\":2,\"ledger\":{}}}")));
  }

  @Test
  void full_fetch_stops_at_empty_page() throws IOException {
    stubLedgerPage("ledger", false);
    stubEmptyLedgerPage(true);

    java.util.Map<String, KrakenLedger> ledgers =
        rawAccountService().getKrakenLedgerInfo(null, null, null, null);

    assertThat(ledgers).hasSize(3);
    // exactly two pages: the first data page and the empty terminator page
    verify(2, postRequestedFor(urlEqualTo("/0/private/Ledgers")));
  }

  @Test
  void repeated_page_without_progress_fails() throws IOException {
    stubLedgerPage("ledger", false);
    stubLedgerPage("ledger", true);

    assertThatExceptionOfType(ExchangeException.class)
        .isThrownBy(() -> rawAccountService().getKrakenLedgerInfo(null, null, null, null))
        .withMessageContaining("no progress");
  }
}
