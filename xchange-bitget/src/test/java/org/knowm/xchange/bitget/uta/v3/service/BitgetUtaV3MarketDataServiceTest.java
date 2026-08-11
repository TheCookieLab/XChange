package org.knowm.xchange.bitget.uta.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ExchangeWiremock;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.service.marketdata.MarketDataService;

class BitgetUtaV3MarketDataServiceTest extends BitgetUtaV3ExchangeWiremock {

  private final MarketDataService marketDataService = exchange.getMarketDataService();

  @Test
  void ticker_maps_wire_fields() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/market/tickers"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":[{\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"ts\":\"1725040472073\","
                            + "\"lastPrice\":\"60000\",\"openPrice24h\":\"58000\",\"highPrice24h\":\"61000\","
                            + "\"lowPrice24h\":\"57000\",\"ask1Price\":\"60001\",\"bid1Price\":\"59999\","
                            + "\"bid1Size\":\"0.5\",\"ask1Size\":\"0.6\",\"price24hPcnt\":\"0.0345\","
                            + "\"volume24h\":\"120.5\",\"turnover24h\":\"7200000\","
                            + "\"platformTurnover24h\":\"7200000\"}]}")));

    Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USDT);

    assertThat(ticker.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(ticker.getLast()).isEqualByComparingTo("60000");
    assertThat(ticker.getOpen()).isEqualByComparingTo("58000");
    assertThat(ticker.getHigh()).isEqualByComparingTo("61000");
    assertThat(ticker.getLow()).isEqualByComparingTo("57000");
    assertThat(ticker.getAsk()).isEqualByComparingTo("60001");
    assertThat(ticker.getBid()).isEqualByComparingTo("59999");
    assertThat(ticker.getBidSize()).isEqualByComparingTo("0.5");
    assertThat(ticker.getAskSize()).isEqualByComparingTo("0.6");
    assertThat(ticker.getVolume()).isEqualByComparingTo("120.5");
    assertThat(ticker.getQuoteVolume()).isEqualByComparingTo("7200000");
  }

  @Test
  void order_book_maps_ask_bid_levels() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/market/orderbook"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"a\":[[\"60001\",\"1.5\"],[\"60002\",\"2.0\"]],"
                            + "\"b\":[[\"59999\",\"3.0\"],[\"59998\",\"1.0\"]],\"ts\":1725040472073}}")));

    OrderBook book = marketDataService.getOrderBook(CurrencyPair.BTC_USDT, 2);

    assertThat(book.getAsks()).hasSize(2);
    assertThat(book.getAsks().get(0).getLimitPrice()).isEqualByComparingTo("60001");
    assertThat(book.getAsks().get(0).getOriginalAmount()).isEqualByComparingTo("1.5");
    assertThat(book.getBids()).hasSize(2);
    assertThat(book.getBids().get(0).getLimitPrice()).isEqualByComparingTo("59999");
    assertThat(book.getBids().get(0).getOriginalAmount()).isEqualByComparingTo("3.0");
  }

  @Test
  void all_tickers_sweeps_categories() throws Exception {
    for (String category : new String[] {"spot", "usdt-futures", "coin-futures", "usdc-futures"}) {
      wireMockServer.stubFor(
          get(urlPathEqualTo("/api/v3/market/tickers"))
              .withQueryParam(
                  "category", com.github.tomakehurst.wiremock.client.WireMock.equalTo(category))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                              + "\"data\":[{\"category\":\""
                              + category
                              + "\",\"symbol\":\"BTCUSDT\",\"ts\":\"1725040472073\","
                              + "\"lastPrice\":\"60000\",\"volume24h\":\"120.5\"}]}")));
    }

    List<Ticker> tickers = marketDataService.getTickers(null);

    assertThat(tickers).hasSize(4);
    assertThat(tickers).allSatisfy(t -> assertThat(t.getLast()).isEqualByComparingTo("60000"));
  }

  @Test
  void metadata_includes_online_instruments() {
    // remoteInit ran at exchange creation against the instruments stub
    assertThat(exchange.getExchangeMetaData().getInstruments()).containsKeys(CurrencyPair.BTC_USDT);
  }
}
