package org.knowm.xchange.gateio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.recording.RecordSpecBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;

/** Sets up the wiremock for exchange */
public abstract class GateioExchangeWiremock {

  protected static GateioExchange exchange;

  //  private static final boolean IS_RECORDING = true;
  private static final boolean IS_RECORDING = false;

  private static WireMockServer wireMockServer;

  @BeforeAll
  public static void initExchange() {    wireMockServer = new WireMockServer(options().dynamicPort());
    wireMockServer.start();

    ExchangeSpecification exSpec = new ExchangeSpecification(GateioExchange.class);
    exSpec.setSslUri("http://localhost:" + wireMockServer.port());
    exSpec.setApiKey("a");
    exSpec.setSecretKey("b");

    if (IS_RECORDING) {
      // use default url and record the requests
      wireMockServer.startRecording(
          new RecordSpecBuilder()
              .forTarget("https://api.gateio.ws")
              .matchRequestBodyWithEqualToJson()
              .extractTextBodiesOver(1L)
              .chooseBodyMatchTypeAutomatically());
    }

    registerFullPageStubs();

    exchange = (GateioExchange) ExchangeFactory.INSTANCE.createExchange(exSpec);
  }

  /**
   * Registers the full-page (1,000-record) page-1 responses in code instead of committing
   * twenty-thousand-line fixtures. The page grid is exercised against the provider's constant
   * page size, so the response must really contain {@code PAGE_SIZE} records; a generated stub
   * keeps the committed payloads minimal while preserving the boundary behavior.
   */
  private static void registerFullPageStubs() {
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v4/spot/my_trades?currency_pair=BTC_USDT&limit=1000&page=1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(tradesPageBody(PAGE_SIZE, "BTC_USDT", 6068816979L))));
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v4/spot/my_trades?currency_pair=ETH_USDT&limit=1000&page=1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(tradesPageBody(PAGE_SIZE, "ETH_USDT", 6068816979L))));
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v4/spot/account_book?currency=USDT&limit=1000&page=1&type=order_fee"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(accountBookPageBody(PAGE_SIZE, 40558668441L))));
  }

  /** The provider-side page size the bounded iteration runs against. */
  private static final int PAGE_SIZE = 1000;

  private static String tradesPageBody(int count, String currencyPair, long firstId) {
    StringBuilder body = new StringBuilder("[");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        body.append(',');
      }
      body.append("{\"id\":\"").append(firstId - i).append("\",")
          .append("\"create_time\":\"1691702924\",")
          .append("\"create_time_ms\":\"1691702924010.071000\",")
          .append("\"currency_pair\":\"").append(currencyPair).append("\",")
          .append("\"side\":\"buy\",\"role\":\"maker\",")
          .append("\"amount\":\"0.00003\",\"price\":\"29454.6\",")
          .append("\"order_id\":\"381068734893\",\"fee\":\"0.00000006\",")
          .append("\"fee_currency\":\"BTC\",\"point_fee\":\"0\",\"gt_fee\":\"0\",")
          .append("\"amend_text\":\"-\"}");
    }
    return body.append(']').toString();
  }

  private static String accountBookPageBody(int count, long firstId) {
    StringBuilder body = new StringBuilder("[");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        body.append(',');
      }
      body.append("{\"id\":\"").append(firstId - i).append("\",")
          .append("\"time\":1691510500000,")
          .append("\"currency\":\"USDT\",\"change\":\"1.5\",\"balance\":\"11.8\",")
          .append("\"type\":\"deposit\"}");
    }
    return body.append(']').toString();
  }

  @AfterAll
  public static void stop() {
    if (IS_RECORDING) {
      wireMockServer.stopRecording();
    }
    wireMockServer.stop();
  }

  /** The running WireMock instance; subclasses verify request counts through it. */
  protected static WireMockServer wireMockServer() {
    return wireMockServer;
  }
}
