package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.marketdata.instruments.BybitInstrumentInfo;
import org.knowm.xchange.exceptions.ExchangeException;

/** Cursor-complete catalog pagination with runaway-pagination guards. */
public class BybitInstrumentsPaginationTest extends BaseWiremockTest {

  private BybitMarketDataServiceRaw marketDataServiceRaw;

  @Before
  public void setUp() throws Exception {
    Exchange bybitExchange = createExchange();
    marketDataServiceRaw = (BybitMarketDataServiceRaw) bybitExchange.getMarketDataService();
  }

  private void stubLinearPage(StringValuePattern cursorMatcher, String responseBody)
      throws IOException {
    stubFor(
        get(urlPathEqualTo("/v5/market/instruments-info"))
            .withQueryParam("category", equalTo("linear"))
            .withQueryParam("cursor", cursorMatcher)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(IOUtils.resourceToString(responseBody, StandardCharsets.UTF_8))));
  }

  private void stubLinearPageWithoutCursorConstraint(String responseBody) throws IOException {
    stubFor(
        get(urlPathEqualTo("/v5/market/instruments-info"))
            .withQueryParam("category", equalTo("linear"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(IOUtils.resourceToString(responseBody, StandardCharsets.UTF_8))));
  }

  @Test
  public void followsNextPageCursorUntilEmpty() throws Exception {
    stubLinearPage(absent(), "/getInstrumentLinearPage1.json5");
    stubLinearPage(equalTo("page-1"), "/getInstrumentLinearPage2.json5");

    List<BybitInstrumentInfo> instruments =
        marketDataServiceRaw.getAllInstrumentsInfo(BybitCategory.LINEAR);

    assertThat(instruments).hasSize(2);
    assertThat(instruments.get(0).getSymbol()).isEqualTo("BTCUSDT");
    assertThat(instruments.get(1).getSymbol()).isEqualTo("ETHUSDT");
  }

  @Test
  public void singlePageWithoutCursorTerminates() throws Exception {
    stubLinearPageWithoutCursorConstraint("/getInstrumentLinear.json5");

    List<BybitInstrumentInfo> instruments =
        marketDataServiceRaw.getAllInstrumentsInfo(BybitCategory.LINEAR);

    assertThat(instruments).hasSize(1);
    assertThat(instruments.get(0).getSymbol()).isEqualTo("BTCUSDT");
  }

  @Test
  public void repeatedCursorAbortsInsteadOfLooping() throws Exception {
    stubLinearPageWithoutCursorConstraint("/getInstrumentLinearPage1.json5");

    Throwable thrown =
        catchThrowable(() -> marketDataServiceRaw.getAllInstrumentsInfo(BybitCategory.LINEAR));

    assertThat(thrown)
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("repeated cursor")
        .hasMessageContaining("infinite loop");
  }

  @Test
  public void emptyPageWithCursorAbortsInsteadOfLooping() throws Exception {
    stubFor(
        get(urlPathEqualTo("/v5/market/instruments-info"))
            .withQueryParam("category", equalTo("linear"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"retCode\":0,\"retMsg\":\"OK\",\"result\":{\"category\":\"linear\","
                            + "\"list\":[],\"nextPageCursor\":\"page-1\"},\"retExtInfo\":{},\"time\":1}")));

    Throwable thrown =
        catchThrowable(() -> marketDataServiceRaw.getAllInstrumentsInfo(BybitCategory.LINEAR));

    assertThat(thrown)
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("no progress");
  }
}
