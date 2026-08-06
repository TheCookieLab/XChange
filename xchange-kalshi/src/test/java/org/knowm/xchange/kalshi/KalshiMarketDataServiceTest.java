package org.knowm.xchange.kalshi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
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

/**
 * Wire-level tests for {@link KalshiMarketDataService} against provider-current Kalshi payloads:
 * fixed-point dollar prices with four decimals, fixed-point counts with two decimals, {@code
 * price_ranges} on markets, missing optional fields, and additive unknown fields the DTOs must
 * tolerate. The fixtures are pinned to the live schema so a future provider migration turns CI
 * red rather than silently producing an empty book.
 */
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
  void getTickerAdaptsFixedPointQuotes() throws Exception {
    server.stubFor(
        get(urlEqualTo("/trade-api/v2/markets/" + TICKER))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"market\":{\"ticker\":\""
                            + TICKER
                            + "\",\"event_ticker\":\"KXBTC-25DEC31\",\"title\":\"BTC\","
                            + "\"status\":\"active\",\"yes_bid_dollars\":\"0.4217\","
                            + "\"yes_ask_dollars\":\"0.4400\",\"last_price_dollars\":\"0.4300\","
                            + "\"volume_fp\":\"1000.50\",\"open_interest_fp\":\"500.00\","
                            + "\"notional_value_dollars\":\"1.0000\","
                            + "\"price_ranges\":[{\"start\":\"0.0000\",\"end\":\"0.1000\","
                            + "\"step\":\"0.0010\"},{\"start\":\"0.1000\",\"end\":\"0.9000\","
                            + "\"step\":\"0.0100\"},{\"start\":\"0.9000\",\"end\":\"1.0000\","
                            + "\"step\":\"0.0010\"}],\"future_field\":\"tolerated\"}}")));

    Ticker ticker = service.getTicker(KalshiAdapters.contractForTicker(TICKER));
    assertThat(ticker.getBid()).isEqualByComparingTo("0.4217");
    assertThat(ticker.getAsk()).isEqualByComparingTo("0.4400");
    assertThat(ticker.getLast()).isEqualByComparingTo("0.4300");
    assertThat(ticker.getVolume()).isEqualByComparingTo("1000.50");
  }

  @Test
  void getOrderBookAdaptsYesAndNoLevels() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/markets/" + TICKER + "/orderbook"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"orderbook_fp\":{\"yes_dollars\":[[\"0.5300\",\"100.00\"],"
                            + "[\"0.5000\",\"10.00\"]],\"no_dollars\":[[\"0.4500\",\"80.00\"]]},"
                            + "\"future_field\":\"tolerated\"}}")));

    OrderBook book = service.getOrderBook(KalshiAdapters.contractForTicker(TICKER));
    assertThat(book.getBids()).hasSize(2);
    assertThat(book.getAsks()).hasSize(1);
    assertThat(book.getBids().get(0).getLimitPrice()).isEqualByComparingTo("0.5300");
    assertThat(book.getBids().get(1).getLimitPrice()).isEqualByComparingTo("0.5000");
    assertThat(book.getAsks().get(0).getLimitPrice()).isEqualByComparingTo("0.5500");
    assertThat(book.getAsks().get(0).getOriginalAmount()).isEqualByComparingTo("80.00");
  }

  @Test
  void getTradesMapsAskTakerToAskAggressor() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/markets/trades"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"trades\":["
                            + "{\"trade_id\":\"t-1\",\"ticker\":\""
                            + TICKER
                            + "\",\"count_fp\":\"5.50\",\"yes_price_dollars\":\"0.5300\","
                            + "\"taker_book_side\":\"ask\",\"created_time\":\"2026-01-01T00:00:00Z\"},"
                            + "{\"trade_id\":\"t-2\",\"ticker\":\""
                            + TICKER
                            + "\",\"count_fp\":\"13.50\",\"yes_price_dollars\":\"0.4217\","
                            + "\"taker_book_side\":\"bid\"}],\"cursor\":\"\"}")));

    Trades trades = service.getTrades(KalshiAdapters.contractForTicker(TICKER));
    assertEquals(2, trades.getTrades().size());
    assertEquals(OrderType.ASK, trades.getTrades().get(0).getType());
    assertThat(trades.getTrades().get(0).getPrice()).isEqualByComparingTo("0.5300");
    assertThat(trades.getTrades().get(0).getOriginalAmount()).isEqualByComparingTo("5.50");
    assertEquals("t-1", trades.getTrades().get(0).getId());
    assertEquals(OrderType.BID, trades.getTrades().get(1).getType());
    assertThat(trades.getTrades().get(1).getPrice()).isEqualByComparingTo("0.4217");
  }

  @Test
  void nonContractInstrumentsAreRejectedBeforeAnyHttpCall() {
    assertThrows(
        InstrumentNotValidException.class, () -> service.getTicker(CurrencyPair.BTC_USD));
    assertEquals(0, server.getAllServeEvents().size());
  }
}
