package org.knowm.xchange.kucoin.uta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.kucoin.uta.dto.UtaInstrument;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderBook;
import org.knowm.xchange.kucoin.uta.dto.UtaTicker;

class UtaMarketDataServiceTest extends AbstractUtaResilienceTest {

  private static final String INSTRUMENTS =
      "{\"code\":\"200000\",\"data\":{\"tradeType\":\"SPOT\",\"list\":["
          + "{\"symbol\":\"BTC-USDT\",\"name\":\"BTC-USDT\",\"baseCurrency\":\"BTC\","
          + "\"quoteCurrency\":\"USDT\",\"minBaseOrderSize\":\"0.00001\","
          + "\"maxBaseOrderSize\":\"10000000000\",\"minQuoteOrderSize\":\"0.1\","
          + "\"maxQuoteOrderSize\":\"99999999\",\"baseOrderStep\":\"0.00000001\","
          + "\"quoteOrderStep\":\"0.000001\",\"tickSize\":\"0.1\",\"feeCurrency\":\"USDT\","
          + "\"tradingStatus\":\"1\"}]}}";

  private static final String TICKERS =
      "{\"code\":\"200000\",\"data\":{\"tradeType\":\"FUTURES\",\"ts\":1782119154577000000,\"list\":["
          + "{\"symbol\":\"XBTUSDTM\",\"bestBidPrice\":\"64230.8\",\"bestBidSize\":\"3\","
          + "\"bestAskPrice\":\"64230.9\",\"bestAskSize\":\"545\",\"lastPrice\":\"64235.9\","
          + "\"high\":\"64824.9\",\"low\":\"63261.6\",\"baseVolume\":\"6956.142\","
          + "\"quoteVolume\":\"445459241.1278\",\"open\":\"64001.3\",\"priceChange\":\"234.6\","
          + "\"priceChangePercent\":\"0.3666\"}]}}";

  private static final String ORDERBOOK =
      "{\"code\":\"200000\",\"data\":{\"tradeType\":\"SPOT\",\"symbol\":\"BTC-USDT\","
          + "\"sequence\":27275344291,"
          + "\"bids\":[[\"92671\",\"0.82674146\"]],"
          + "\"asks\":[[\"92671.1\",\"0.13163929\"]]}}";

  private void stubJson(String path, String body) {
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(path))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  @Test
  void publicCatalogRequiresNoAuthHeaders() throws Exception {
    stubJson("/api/ua/v1/market/instrument", INSTRUMENTS);

    List<UtaInstrument> instruments =
        createUtaExchange().getUtaMarketDataService().getUtaInstruments("SPOT");

    assertEquals(1, instruments.size());
    assertEquals("BTC-USDT", instruments.get(0).getSymbol());
    assertEquals(new BigDecimal("0.00001"), instruments.get(0).getMinBaseOrderSize());
    assertEquals(new BigDecimal("0.1"), instruments.get(0).getTickSize());
    wireMockRule.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/ua/v1/market/instrument"))
            .withoutHeader("KC-API-KEY")
            .withoutHeader("KC-API-SIGN"));
  }

  @Test
  void parsesFuturesTicker() throws Exception {
    stubJson("/api/ua/v1/market/ticker", TICKERS);

    UtaTicker raw =
        createUtaExchange().getUtaMarketDataService().getUtaTickers("FUTURES", "XBTUSDTM").getList().get(0);
    assertEquals(new BigDecimal("64230.8"), raw.getBestBidPrice());

    Ticker ticker =
        createUtaExchange()
            .getUtaMarketDataService()
            .getUtaTicker(UtaAdapters.instrumentForSymbol("XBTUSDTM"));
    assertEquals(new BigDecimal("64235.9"), ticker.getLast());
  }

  @Test
  void parsesOrderBookWithSequence() throws Exception {
    stubJson("/api/ua/v1/market/orderbook", ORDERBOOK);

    UtaOrderBook book =
        createUtaExchange().getUtaMarketDataService().getUtaOrderBook("SPOT", "BTC-USDT", "FULL");
    assertEquals(27275344291L, book.getSequence());
    assertEquals(new BigDecimal("92671"), book.getBids().get(0).get(0));
    assertTrue(book.getAsks().get(0).get(1).compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void orderBookSortsBothSidesAndPreservesEqualPriceOrder() {
    UtaOrderBook raw = new UtaOrderBook();
    raw.setAsks(List.of(level("12", "1"), level("10", "2"), level("10.0", "3")));
    raw.setBids(List.of(level("8", "4"), level("10", "5"), level("10.0", "6")));

    OrderBook book = UtaAdapters.adaptOrderBook(CurrencyPair.BTC_USDT, raw);

    assertEquals(
        List.of(new BigDecimal("10"), new BigDecimal("10.0"), new BigDecimal("12")),
        book.getAsks().stream().map(LimitOrder::getLimitPrice).toList());
    assertEquals(
        List.of(new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("1")),
        book.getAsks().stream().map(LimitOrder::getOriginalAmount).toList());
    assertEquals(
        List.of(new BigDecimal("10"), new BigDecimal("10.0"), new BigDecimal("8")),
        book.getBids().stream().map(LimitOrder::getLimitPrice).toList());
    assertEquals(
        List.of(new BigDecimal("5"), new BigDecimal("6"), new BigDecimal("4")),
        book.getBids().stream().map(LimitOrder::getOriginalAmount).toList());
  }

  @Test
  void orderBookAllowsEmptySidesAndRejectsNullPricesDuringSorting() {
    UtaOrderBook raw = new UtaOrderBook();
    raw.setAsks(List.of());
    raw.setBids(List.of());
    OrderBook empty = UtaAdapters.adaptOrderBook(CurrencyPair.BTC_USDT, raw);
    assertTrue(empty.getAsks().isEmpty());
    assertTrue(empty.getBids().isEmpty());

    List<List<BigDecimal>> nullPrice =
        List.of(Arrays.asList(null, BigDecimal.ONE), level("1", "2"));
    raw.setAsks(nullPrice);
    assertThrows(
        NullPointerException.class, () -> UtaAdapters.adaptOrderBook(CurrencyPair.BTC_USDT, raw));
    raw.setAsks(List.of());
    raw.setBids(nullPrice);
    assertThrows(
        NullPointerException.class, () -> UtaAdapters.adaptOrderBook(CurrencyPair.BTC_USDT, raw));
  }

  private static List<BigDecimal> level(String price, String size) {
    return List.of(new BigDecimal(price), new BigDecimal(size));
  }

  @Test
  void futuresInstrumentMapsToFuturesContract() {
    UtaInstrument instrument = new UtaInstrument();
    instrument.setSymbol("XBTUSDTM");
    instrument.setBaseCurrency("XBT");
    instrument.setQuoteCurrency("USDT");
    instrument.setContractType("0");
    instrument.setIsInverse(false);
    assertTrue(
        UtaAdapters.adaptInstrument("FUTURES", instrument)
            instanceof org.knowm.xchange.derivative.FuturesContract);
    assertFalse(
        UtaAdapters.adaptInstrument("SPOT", instrument)
            instanceof org.knowm.xchange.derivative.FuturesContract);
  }
}
