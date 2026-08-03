package org.knowm.xchange.polymarket;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.polymarket.service.PolymarketMarketDataService;
import org.knowm.xchange.prediction.PredictionMarketContract;

/** Wire-level tests for {@link PolymarketMarketDataService} over the CLOB and Data hosts. */
class PolymarketMarketDataServiceTest {

  private static final String CONDITION_ID = "0xdd22472e";
  private static final String TOKEN_ID = "713210456792522125";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID, Currency.USD);

  private static final String BOOK_BODY =
      "{\"market\":\""
          + CONDITION_ID
          + "\",\"asset_id\":\""
          + TOKEN_ID
          + "\",\"timestamp\":\"1754230000000\",\"hash\":\"0x\","
          + "\"bids\":[{\"price\":\"0.55\",\"size\":\"100\"},{\"price\":\"0.56\",\"size\":\"50\"}],"
          + "\"asks\":[{\"price\":\"0.60\",\"size\":\"70\"},{\"price\":\"0.59\",\"size\":\"80\"}]}";

  private WireMockServer server;
  private PolymarketMarketDataService service;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    PolymarketExchange exchange = new PolymarketExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setSslUri(server.baseUrl());
    spec.setExchangeSpecificParametersItem(PolymarketExchange.PARAM_GAMMA_URI, server.baseUrl());
    spec.setExchangeSpecificParametersItem(PolymarketExchange.PARAM_DATA_URI, server.baseUrl());
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    service = (PolymarketMarketDataService) exchange.getMarketDataService();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void orderBookIsSortedBestFirstWithContractIdentity() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/book"))
            .withQueryParam("token_id", equalTo(TOKEN_ID))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(BOOK_BODY)));

    OrderBook book = service.getOrderBook(CONTRACT);
    assertEquals(new BigDecimal("0.56"), book.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("0.59"), book.getAsks().get(0).getLimitPrice());
    assertEquals(CONTRACT, book.getBids().get(0).getInstrument());
    assertEquals(new Date(1754230000000L), book.getTimeStamp());
  }

  @Test
  void tickerReadsTopOfBook() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/book"))
            .withQueryParam("token_id", equalTo(TOKEN_ID))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(BOOK_BODY)));

    Ticker ticker = service.getTicker(CONTRACT);
    assertEquals(new BigDecimal("0.56"), ticker.getBid());
    assertEquals(new BigDecimal("0.59"), ticker.getAsk());
    assertEquals(CONTRACT, ticker.getInstrument());
  }

  @Test
  void tradesComeFromTheDataApiKeyedByCondition() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trades"))
            .withQueryParam("market", equalTo(CONDITION_ID))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"proxyWallet\":\"0x\",\"side\":\"SELL\",\"asset\":\""
                            + TOKEN_ID
                            + "\",\"conditionId\":\""
                            + CONDITION_ID
                            + "\",\"size\":3,\"price\":0.56,\"timestamp\":1754230000,"
                            + "\"title\":\"t\",\"outcome\":\"Yes\",\"outcomeIndex\":0,"
                            + "\"transactionHash\":\"0xhash\"}]")));

    Trades trades = service.getTrades(CONTRACT);
    assertEquals(1, trades.getTrades().size());
    assertEquals(OrderType.ASK, trades.getTrades().get(0).getType());
    assertEquals(CONTRACT, trades.getTrades().get(0).getInstrument());
    assertEquals(new Date(1754230000L * 1000L), trades.getTrades().get(0).getTimestamp());
  }

  @Test
  void currencyPairsAreRejectedWithoutHttpCall() {
    assertThrows(
        InstrumentNotValidException.class, () -> service.getOrderBook(CurrencyPair.BTC_USD));
    assertEquals(0, server.getAllServeEvents().size());
  }
}
