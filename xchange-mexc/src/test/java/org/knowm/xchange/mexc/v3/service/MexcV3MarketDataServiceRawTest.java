package org.knowm.xchange.mexc.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.mexc.v3.BaseMexcV3WiremockTest;
import org.knowm.xchange.mexc.v3.MexcV3Exchange;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Kline;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3KlineInterval;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Symbol;

/** Raw DTO-level coverage of the public MEXC Spot v3 market-data endpoints (WireMock). */
public class MexcV3MarketDataServiceRawTest extends BaseMexcV3WiremockTest {

  private static final String EXCHANGE_INFO =
      "{"
          + "\"timezone\":\"UTC\","
          + "\"serverTime\":1565246363776,"
          + "\"rateLimits\":[{\"rateLimitType\":\"REQUEST_WEIGHT\",\"interval\":\"MINUTE\","
          + "\"intervalNum\":1,\"limit\":6000}],"
          + "\"exchangeFilters\":[],"
          + "\"symbols\":["
          + "{\"symbol\":\"BTCUSDT\",\"status\":\"1\",\"baseAsset\":\"BTC\","
          + "\"baseAssetPrecision\":8,\"quoteAsset\":\"USDT\",\"quotePrecision\":8,"
          + "\"quoteAssetPrecision\":8,\"baseCommissionPrecision\":8,"
          + "\"quoteCommissionPrecision\":8,\"orderTypes\":[\"LIMIT\",\"MARKET\"],"
          + "\"isSpotTradingAllowed\":true,\"isMarginTradingAllowed\":true,"
          + "\"quoteAmountPrecision\":\"0.000001\",\"baseSizePrecision\":\"0.001\","
          + "\"permissions\":[\"SPOT\"],"
          + "\"filters\":[{\"filterType\":\"PERCENT_PRICE_BY_SIDE\",\"bidMultiplierUp\":\"1.1\","
          + "\"bidMultiplierDown\":\"0.9\",\"askMultiplierUp\":\"1.1\",\"askMultiplierDown\":\"0.9\","
          + "\"avgPriceMins\":5}],"
          + "\"maxQuoteAmount\":\"500000\",\"makerCommission\":\"0.002\","
          + "\"takerCommission\":\"0.002\",\"quoteAmountPrecisionMarket\":\"5\","
          + "\"maxQuoteAmountMarket\":\"500000\",\"fullName\":\"Bitcoin/USDT\","
          + "\"tradeSideType\":1,\"contractAddress\":\"\",\"conceptPlateIds\":[],"
          + "\"firstOpenTime\":1536062400000,\"st\":\"1\"},"
          + "{\"symbol\":\"ETHUSDT\",\"status\":\"0\",\"baseAsset\":\"ETH\","
          + "\"baseAssetPrecision\":8,\"quoteAsset\":\"USDT\",\"quotePrecision\":8,"
          + "\"quoteAssetPrecision\":8,\"baseCommissionPrecision\":8,"
          + "\"quoteCommissionPrecision\":8,\"orderTypes\":[\"LIMIT\"],"
          + "\"isSpotTradingAllowed\":true,\"isMarginTradingAllowed\":false,"
          + "\"quoteAmountPrecision\":\"0.000001\",\"baseSizePrecision\":\"0.001\","
          + "\"permissions\":[\"SPOT\"],\"filters\":[],\"maxQuoteAmount\":\"500000\","
          + "\"makerCommission\":\"0.002\",\"takerCommission\":\"0.002\","
          + "\"quoteAmountPrecisionMarket\":\"5\",\"maxQuoteAmountMarket\":\"500000\","
          + "\"fullName\":\"Ethereum/USDT\",\"tradeSideType\":1,\"contractAddress\":\"\","
          + "\"conceptPlateIds\":[],\"firstOpenTime\":1536062400000,\"st\":\"0\"}"
          + "]}";

  @Test
  public void pingReturnsPong() throws IOException {
    stubFor(get(urlPathEqualTo("/api/v3/ping")).willReturn(aResponse().withBody("\"pong\"")));

    MexcV3Exchange exchange = createExchange();
    assertThat(new MexcV3MarketDataServiceRaw(exchange).ping()).isEqualTo("pong");
  }

  @Test
  public void serverTimeIsParsed() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/time"))
            .willReturn(aResponse().withBody("{\"serverTime\":1565246363776}")));

    MexcV3Exchange exchange = createExchange();
    MexcV3MarketDataServiceRaw raw = new MexcV3MarketDataServiceRaw(exchange);
    assertThat(raw.getServerTime()).isEqualTo(1565246363776L);
    assertThat(raw.getServerTimeDto().getServerTime()).isEqualTo(1565246363776L);
  }

  @Test
  public void exchangeInfoKeepsProviderFieldsVerbatim() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/exchangeInfo"))
            .willReturn(aResponse().withBody(EXCHANGE_INFO)));

    MexcV3Exchange exchange = createExchange();
    var info = new MexcV3MarketDataServiceRaw(exchange).getExchangeInfo();

    assertThat(info.getServerTime()).isEqualTo(1565246363776L);
    assertThat(info.getTimezone()).isEqualTo("UTC");
    assertThat(info.getRateLimits()).hasSize(1);
    assertThat(info.getSymbols()).hasSize(2);
    MexcV3Symbol btcusdt =
        info.getSymbols().stream()
            .filter(s -> "BTCUSDT".equals(s.getSymbol()))
            .findFirst()
            .orElseThrow();
    assertThat(btcusdt.getStatus()).isEqualTo("1");
    assertThat(btcusdt.getBaseAsset()).isEqualTo("BTC");
    assertThat(btcusdt.getQuoteAssetPrecision()).isEqualTo(8);
    assertThat(btcusdt.getBaseSizePrecision()).isEqualTo("0.001");
    assertThat(btcusdt.getQuoteAmountPrecision()).isEqualTo("0.000001");
    assertThat(btcusdt.getOrderTypes()).containsExactly("LIMIT", "MARKET");
    assertThat(btcusdt.isSpotTradingAllowed()).isTrue();
    assertThat(btcusdt.getFilters()).hasSize(1);
    assertThat(btcusdt.getFilters().get(0).getFilterType()).isEqualTo("PERCENT_PRICE_BY_SIDE");
    assertThat(btcusdt.getFilters().get(0).getBidMultiplierUp()).isEqualTo("1.1");
  }

  @Test
  public void exchangeInfoByPairPassesSymbolQuery() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/exchangeInfo"))
            .willReturn(aResponse().withBody(EXCHANGE_INFO)));

    MexcV3Exchange exchange = createExchange();
    var info = new MexcV3MarketDataServiceRaw(exchange).getExchangeInfo(CurrencyPair.BTC_USDT);

    assertThat(info.getSymbols()).hasSize(2);
  }

  @Test
  public void klinesParseExactDecimals() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
            .willReturn(
                aResponse()
                    .withBody(
                        "[[1551000000000,\"0.0030\",\"0.0037\",\"0.0035\",\"0.0035\","
                            + "\"206116.70000000\",1551046400000,\"688.20000000\"],"
                            + "[1551086400000,\"0.0035\",\"0.0048\",\"0.0048\",\"0.0047\","
                            + "\"199321.00000000\",1551132800000,\"741.30000000\"]]")));

    MexcV3Exchange exchange = createExchange();
    List<MexcV3Kline> klines =
        new MexcV3MarketDataServiceRaw(exchange)
            .getKlines(CurrencyPair.BTC_USDT, MexcV3KlineInterval.M1, null, null, 2);

    assertThat(klines).hasSize(2);
    MexcV3Kline first = klines.get(0);
    assertThat(first.getOpenTime()).isEqualTo(1551000000000L);
    assertThat(first.getOpen()).isEqualTo("0.0030");
    assertThat(first.getClose()).isEqualTo("0.0035");
    assertThat(first.getVolume()).isEqualTo("206116.70000000");
    assertThat(first.getCloseTime()).isEqualTo(1551046400000L);
    assertThat(first.getQuoteAssetVolume()).isEqualTo("688.20000000");
  }

  @Test
  public void malformedKlineRowFailsLoudly() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
            .willReturn(
                aResponse()
                    .withBody("[[1551000000000,\"0.0030\",\"0.0037\",\"0.0035\",\"0.0035\","
                        + "\"206116.70000000\",1551046400000]]")));

    MexcV3Exchange exchange = createExchange();
    assertThatThrownBy(
            () ->
                new MexcV3MarketDataServiceRaw(exchange)
                    .getKlines(CurrencyPair.BTC_USDT, MexcV3KlineInterval.M1, null, null, 2))
        .isInstanceOf(Exception.class);
  }

  @Test
  public void depthKeepsLevelsVerbatim() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/depth"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"lastUpdateId\":1027024,"
                            + "\"bids\":[[\"4.00000000\",\"431.00000000\"]],"
                            + "\"asks\":[[\"4.00000200\",\"12.00000000\"]]}")));

    MexcV3Exchange exchange = createExchange();
    var depth = new MexcV3MarketDataServiceRaw(exchange).getDepth(CurrencyPair.BTC_USDT, 5);

    assertThat(depth.getLastUpdateId()).isEqualTo(1027024L);
    assertThat(depth.getBids()).hasSize(1);
    assertThat(depth.getBids().get(0).getPrice()).isEqualTo("4.00000000");
    assertThat(depth.getBids().get(0).getQuantity()).isEqualTo("431.00000000");
    assertThat(depth.getAsks().get(0).getPrice()).isEqualTo("4.00000200");
  }

  @Test
  public void tradesParseBuyerMakerFlag() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/trades"))
            .willReturn(
                aResponse()
                    .withBody(
                        "[{\"price\":\"4.00000100\",\"qty\":\"12.00000000\","
                            + "\"quoteQty\":\"48.000012\",\"time\":1499865549590,"
                            + "\"isBuyerMaker\":false,\"isBestMatch\":true,\"id\":28457}]")));

    MexcV3Exchange exchange = createExchange();
    var trades = new MexcV3MarketDataServiceRaw(exchange).getTrades(CurrencyPair.BTC_USDT, null);

    assertThat(trades).hasSize(1);
    assertThat(trades.get(0).getId()).isEqualTo(28457L);
    assertThat(trades.get(0).getPrice()).isEqualTo("4.00000100");
    assertThat(trades.get(0).getQty()).isEqualTo("12.00000000");
    assertThat(trades.get(0).isBuyerMaker()).isFalse();
  }

  @Test
  public void ticker24hParsesAllFields() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/ticker/24hr"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"priceChange\":\"-94.99999800\","
                            + "\"priceChangePercent\":\"-95.960\",\"prevClosePrice\":\"98.51\","
                            + "\"lastPrice\":\"4.00000200\",\"bidPrice\":\"4.00000000\","
                            + "\"bidQty\":\"431.00000000\",\"askPrice\":\"4.00000200\","
                            + "\"askQty\":\"9.00000000\",\"openPrice\":\"99.00000000\","
                            + "\"highPrice\":\"100.00000000\",\"lowPrice\":\"0.10000000\","
                            + "\"volume\":\"8913.30000000\",\"quoteVolume\":\"15.30000000\","
                            + "\"openTime\":1499783499040,\"closeTime\":1499869859040,"
                            + "\"count\":\"3\"}")));

    MexcV3Exchange exchange = createExchange();
    var ticker =
        new MexcV3MarketDataServiceRaw(exchange).getTicker24h(CurrencyPair.BTC_USDT);

    assertThat(ticker.getSymbol()).isEqualTo("BTCUSDT");
    assertThat(ticker.getPriceChangePercent()).isEqualTo("-95.960");
    assertThat(ticker.getLastPrice()).isEqualTo("4.00000200");
    assertThat(ticker.getBidPrice()).isEqualTo("4.00000000");
    assertThat(ticker.getAskQty()).isEqualTo("9.00000000");
    assertThat(ticker.getHighPrice()).isEqualTo("100.00000000");
    assertThat(ticker.getVolume()).isEqualTo("8913.30000000");
    assertThat(ticker.getQuoteVolume()).isEqualTo("15.30000000");
    assertThat(ticker.getCloseTime()).isEqualTo(1499869859040L);
  }

  @Test
  public void bookAndPriceTickersParse() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/ticker/bookTicker"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"bidPrice\":\"4.00000000\","
                            + "\"bidQty\":\"431.00000000\",\"askPrice\":\"4.00000200\","
                            + "\"askQty\":\"9.00000000\"}")));
    stubFor(
        get(urlPathEqualTo("/api/v3/ticker/price"))
            .willReturn(aResponse().withBody("{\"symbol\":\"BTCUSDT\",\"price\":\"4.00000200\"}")));

    MexcV3Exchange exchange = createExchange();
    MexcV3MarketDataServiceRaw raw = new MexcV3MarketDataServiceRaw(exchange);

    assertThat(raw.getBookTicker(CurrencyPair.BTC_USDT).getBidPrice()).isEqualTo("4.00000000");
    assertThat(raw.getBookTicker(CurrencyPair.BTC_USDT).getAskQty()).isEqualTo("9.00000000");
    assertThat(raw.getPriceTicker(CurrencyPair.BTC_USDT).getPrice()).isEqualTo("4.00000200");
  }

  @Test
  public void avgPriceParses() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/avgPrice"))
            .willReturn(aResponse().withBody("{\"mins\":5,\"price\":\"9.35751834\"}")));

    MexcV3Exchange exchange = createExchange();
    var avg = new MexcV3MarketDataServiceRaw(exchange).getAvgPrice(CurrencyPair.BTC_USDT);

    assertThat(avg.getMins()).isEqualTo(5);
    assertThat(avg.getPrice()).isEqualTo("9.35751834");
  }

  @Test
  public void defaultSymbolsUsesEnvelope() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/defaultSymbols"))
            .willReturn(
                aResponse()
                    .withBody("{\"code\":200,\"data\":[\"BTCUSDT\",\"ETHUSDT\"],\"msg\":null}")));

    MexcV3Exchange exchange = createExchange();
    var symbols = new MexcV3MarketDataServiceRaw(exchange).defaultSymbols();

    assertThat(symbols.getCode()).isEqualTo(200);
    assertThat(symbols.getData()).containsExactly("BTCUSDT", "ETHUSDT");
  }
}
