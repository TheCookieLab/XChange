package org.knowm.xchange.kalshi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.kalshi.service.KalshiMarketDataService;

/** Wire-level tests for {@link KalshiMarketDataService} against canned Kalshi payloads. */
class KalshiMarketDataServiceTest {

  private static final String TICKER = "KXBTC-25DEC31-T90000";

  private WireMockServer server;
  private KalshiMarketDataService service;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    KalshiExchange exchange = new KalshiExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setSslUri(server.baseUrl());
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    service = (KalshiMarketDataService) exchange.getMarketDataService();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void getTickerAdaptsCentsQuotes() throws Exception {
    server.stubFor(
        get(urlEqualTo("/trade-api/v2/markets/" + TICKER))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"market\":{\"ticker\":\""
                            + TICKER
                            + "\",\"event_ticker\":\"KXBTC-25DEC31\",\"title\":\"BTC\","
                            + "\"status\":\"open\",\"yes_bid\":53,\"yes_ask\":54,"
                            + "\"last_price\":52,\"volume\":1000,\"open_interest\":500}}")));

    Ticker ticker = service.getTicker(KalshiAdapters.contractForTicker(TICKER));
    assertEquals(new BigDecimal("0.53"), ticker.getBid());
    assertEquals(new BigDecimal("0.54"), ticker.getAsk());
    assertEquals(new BigDecimal("0.52"), ticker.getLast());
    assertEquals(new BigDecimal("1000"), ticker.getVolume());
  }

  @Test
  void getOrderBookAdaptsYesAndNoLevels() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/markets/" + TICKER + "/orderbook"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"orderbook\":{\"yes\":[[53,100],[50,10]],\"no\":[[45,80]]}}")));

    OrderBook book = service.getOrderBook(KalshiAdapters.contractForTicker(TICKER));
    assertEquals(2, book.getBids().size());
    assertEquals(1, book.getAsks().size());
    assertEquals(new BigDecimal("0.53"), book.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("0.55"), book.getAsks().get(0).getLimitPrice());
  }

  @Test
  void getTradesMapsNoTakerToAskAggressor() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/markets/trades"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"trades\":[{\"trade_id\":\"t-1\",\"ticker\":\""
                            + TICKER
                            + "\",\"count\":5,\"yes_price\":53,\"taker_side\":\"no\","
                            + "\"created_time\":\"2026-01-01T00:00:00Z\"}],\"cursor\":\"\"}")));

    Trades trades = service.getTrades(KalshiAdapters.contractForTicker(TICKER));
    assertEquals(1, trades.getTrades().size());
    assertEquals(OrderType.ASK, trades.getTrades().get(0).getType());
    assertEquals(new BigDecimal("0.53"), trades.getTrades().get(0).getPrice());
    assertEquals("t-1", trades.getTrades().get(0).getId());
  }

  @Test
  void nonContractInstrumentsAreRejectedBeforeAnyHttpCall() {
    assertThrows(
        InstrumentNotValidException.class, () -> service.getTicker(CurrencyPair.BTC_USD));
    assertEquals(0, server.getAllServeEvents().size());
  }
}
