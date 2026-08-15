package org.knowm.xchange.mexc.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.mexc.v3.BaseMexcV3WiremockTest;
import org.knowm.xchange.mexc.v3.MexcV3Exchange;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;

/** High-level adapter coverage of the MEXC Spot v3 market-data service (WireMock). */
public class MexcV3MarketDataServiceTest extends BaseMexcV3WiremockTest {

  @Test
  public void getOrderBookAdaptsSortedLevelsWithExactDecimals() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/depth"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"lastUpdateId\":1027024,"
                            + "\"bids\":[[\"4.00000000\",\"431.00000000\"],"
                            + "[\"3.90000000\",\"100.00000000\"]],"
                            + "\"asks\":[[\"4.00000200\",\"12.00000000\"],"
                            + "[\"4.10000000\",\"5.00000000\"]]}")));

    MexcV3Exchange exchange = createExchange();
    OrderBook book = exchange.getMarketDataService().getOrderBook(CurrencyPair.BTC_USDT, 5);

    assertThat(book.getAsks()).hasSize(2);
    assertThat(book.getBids()).hasSize(2);
    assertThat(book.getAsks().get(0).getType()).isEqualTo(OrderType.ASK);
    assertThat(book.getAsks().get(0).getLimitPrice()).isEqualByComparingTo("4.00000200");
    assertThat(book.getAsks().get(0).getOriginalAmount()).isEqualByComparingTo("12.00000000");
    assertThat(book.getAsks().get(0).getLimitPrice())
        .isLessThan(book.getAsks().get(1).getLimitPrice());
    assertThat(book.getBids().get(0).getLimitPrice()).isGreaterThan(book.getBids().get(1).getLimitPrice());
  }

  @Test
  public void getTickerAdaptsTicker24h() throws IOException {
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
    Ticker ticker = exchange.getMarketDataService().getTicker(CurrencyPair.BTC_USDT);

    assertThat(ticker.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(ticker.getOpen()).isEqualByComparingTo("99.00000000");
    assertThat(ticker.getLast()).isEqualByComparingTo("4.00000200");
    assertThat(ticker.getBid()).isEqualByComparingTo("4.00000000");
    assertThat(ticker.getAsk()).isEqualByComparingTo("4.00000200");
    assertThat(ticker.getHigh()).isEqualByComparingTo("100.00000000");
    assertThat(ticker.getLow()).isEqualByComparingTo("0.10000000");
    assertThat(ticker.getVolume()).isEqualByComparingTo("8913.30000000");
    assertThat(ticker.getQuoteVolume()).isEqualByComparingTo("15.30000000");
    assertThat(ticker.getPercentageChange()).isEqualByComparingTo("-95.960");
    assertThat(ticker.getTimestamp().getTime()).isEqualTo(1499869859040L);
  }

  @Test
  public void getTradesAdaptsBuyerMakerFlag() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/trades"))
            .willReturn(
                aResponse()
                    .withBody(
                        "[{\"price\":\"4.00000100\",\"qty\":\"12.00000000\","
                            + "\"quoteQty\":\"48.000012\",\"time\":1499865549590,"
                            + "\"isBuyerMaker\":false,\"isBestMatch\":true,\"id\":28457},"
                            + "{\"price\":\"4.00000000\",\"qty\":\"1.00000000\","
                            + "\"quoteQty\":\"4.000000\",\"time\":1499865549591,"
                            + "\"isBuyerMaker\":true,\"isBestMatch\":true,\"id\":28458}]")));

    MexcV3Exchange exchange = createExchange();
    Trades trades = exchange.getMarketDataService().getTrades(CurrencyPair.BTC_USDT);

    assertThat(trades.getTrades()).hasSize(2);
    assertThat(trades.getTrades().get(0).getType()).isEqualTo(OrderType.BID);
    assertThat(trades.getTrades().get(0).getPrice()).isEqualByComparingTo("4.00000100");
    assertThat(trades.getTrades().get(0).getOriginalAmount()).isEqualByComparingTo("12.00000000");
    assertThat(trades.getTrades().get(1).getType()).isEqualTo(OrderType.ASK);
  }

  @Test
  public void getCandleStickDataMapsPeriodToInterval() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
            .willReturn(
                aResponse()
                    .withBody(
                        "[[1551000000000,\"0.0030\",\"0.0037\",\"0.0035\",\"0.0035\","
                            + "\"206116.70000000\",1551046400000,\"688.20000000\"]]")));

    MexcV3Exchange exchange = createExchange();
    CandleStickData data =
        exchange
            .getMarketDataService()
            .getCandleStickData(
                CurrencyPair.BTC_USDT, new DefaultCandleStickParamWithLimit(null, null, 60L, 1));

    assertThat(data.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(data.getCandleSticks()).hasSize(1);
    var candle = data.getCandleSticks().get(0);
    assertThat(candle.getTimestamp()).isEqualTo(Instant.ofEpochMilli(1551000000000L));
    assertThat(candle.getOpen()).isEqualByComparingTo("0.0030");
    assertThat(candle.getClose()).isEqualByComparingTo("0.0035");
    assertThat(candle.getHigh()).isEqualByComparingTo("0.0037");
    assertThat(candle.getLow()).isEqualByComparingTo("0.0035");
    assertThat(candle.getVolume()).isEqualByComparingTo("206116.70000000");
    assertThat(candle.getQuotaVolume()).isEqualByComparingTo("688.20000000");
    assertThat(candle.isCompleted()).isTrue();
  }

  @Test
  public void getCandleStickDataMapsWeeklyPeriod() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
            .willReturn(
                aResponse()
                    .withBody(
                        "[[1551000000000,\"0.0030\",\"0.0037\",\"0.0035\",\"0.0035\","
                            + "\"206116.70000000\",1551046400000,\"688.20000000\"]]")));

    MexcV3Exchange exchange = createExchange();
    CandleStickData data =
        exchange
            .getMarketDataService()
            .getCandleStickData(
                CurrencyPair.BTC_USDT,
                new DefaultCandleStickParamWithLimit(null, null, 604800L, 1));

    assertThat(data.getCandleSticks()).hasSize(1);
    verify(
        getRequestedFor(urlPathEqualTo("/api/v3/klines"))
            .withQueryParam("interval", equalTo("1W")));
  }

  @Test
  public void getCandleStickDataRejectsUnknownPeriod() throws IOException {
    MexcV3Exchange exchange = createExchange();
    assertThatThrownBy(
            () ->
                exchange
                    .getMarketDataService()
                    .getCandleStickData(
                        CurrencyPair.BTC_USDT, new DefaultCandleStickParam(null, null, 12345L)))
        .isInstanceOf(ExchangeException.class);
  }

  @Test
  public void getExchangeHealthPings() throws IOException {
    stubFor(get(urlPathEqualTo("/api/v3/ping")).willReturn(aResponse().withBody("\"pong\"")));

    MexcV3Exchange exchange = createExchange();
    assertThat(exchange.getMarketDataService().getExchangeHealth().name()).isEqualTo("ONLINE");
  }

  @Test
  public void remoteInitBuildsMetadataFromExchangeInfo() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/exchangeInfo"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"timezone\":\"UTC\",\"serverTime\":1565246363776,"
                            + "\"rateLimits\":[],\"exchangeFilters\":[],"
                            + "\"symbols\":["
                            + "{\"symbol\":\"BTCUSDT\",\"status\":\"1\",\"baseAsset\":\"BTC\","
                            + "\"baseAssetPrecision\":8,\"quoteAsset\":\"USDT\","
                            + "\"quotePrecision\":8,\"quoteAssetPrecision\":8,"
                            + "\"baseCommissionPrecision\":8,\"quoteCommissionPrecision\":8,"
                            + "\"orderTypes\":[\"LIMIT\",\"MARKET\"],"
                            + "\"isSpotTradingAllowed\":true,\"isMarginTradingAllowed\":false,"
                            + "\"quoteAmountPrecision\":\"0.000001\","
                            + "\"baseSizePrecision\":\"0.001\",\"permissions\":[\"SPOT\"],"
                            + "\"filters\":[],\"maxQuoteAmount\":\"500000\","
                            + "\"makerCommission\":\"0.002\",\"takerCommission\":\"0.002\","
                            + "\"quoteAmountPrecisionMarket\":\"5\","
                            + "\"maxQuoteAmountMarket\":\"500000\",\"fullName\":\"Bitcoin/USDT\","
                            + "\"tradeSideType\":1,\"contractAddress\":\"\","
                            + "\"conceptPlateIds\":[],\"firstOpenTime\":1536062400000,\"st\":\"1\"},"
                            + "{\"symbol\":\"ETHUSDT\",\"status\":\"0\",\"baseAsset\":\"ETH\","
                            + "\"baseAssetPrecision\":8,\"quoteAsset\":\"USDT\","
                            + "\"quotePrecision\":8,\"quoteAssetPrecision\":8,"
                            + "\"baseCommissionPrecision\":8,\"quoteCommissionPrecision\":8,"
                            + "\"orderTypes\":[\"LIMIT\"],"
                            + "\"isSpotTradingAllowed\":true,\"isMarginTradingAllowed\":false,"
                            + "\"quoteAmountPrecision\":\"0.000001\","
                            + "\"baseSizePrecision\":\"0.001\",\"permissions\":[\"SPOT\"],"
                            + "\"filters\":[],\"maxQuoteAmount\":\"500000\","
                            + "\"makerCommission\":\"0.002\",\"takerCommission\":\"0.002\","
                            + "\"quoteAmountPrecisionMarket\":\"5\","
                            + "\"maxQuoteAmountMarket\":\"500000\",\"fullName\":\"Ethereum/USDT\","
                            + "\"tradeSideType\":1,\"contractAddress\":\"\","
                            + "\"conceptPlateIds\":[],\"firstOpenTime\":1536062400000,\"st\":\"0\"}"
                            + "]}\n")));

    MexcV3Exchange exchange =
        (MexcV3Exchange)
            ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(MexcV3Exchange.class);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setHost("localhost");
    specification.setSslUri("http://localhost:" + wireMockRule.port());
    specification.setPort(wireMockRule.port());
    specification.setShouldLoadRemoteMetaData(true);
    exchange.applySpecification(specification);

    ExchangeMetaData metaData = exchange.getExchangeMetaData();
    assertThat(metaData.getInstruments()).containsOnlyKeys(CurrencyPair.BTC_USDT);
    InstrumentMetaData btcusdt = metaData.getInstruments().get(CurrencyPair.BTC_USDT);
    assertThat(btcusdt.getPriceScale()).isEqualTo(8);
    assertThat(btcusdt.getVolumeScale()).isEqualTo(8);
    assertThat(btcusdt.getMinimumAmount()).isEqualByComparingTo("0.001");
    assertThat(btcusdt.getCounterMinimumAmount()).isEqualByComparingTo("0.000001");
    assertThat(btcusdt.isMarketOrderEnabled()).isTrue();
    assertThat(metaData.getCurrencies()).containsKeys(org.knowm.xchange.currency.Currency.BTC);
  }

  @Test
  public void getOpenOrdersParamsAreCurrencyPairBased() throws IOException {
    MexcV3Exchange exchange = createExchange();
    assertThat(exchange.getTradeService().createOpenOrdersParams())
        .isInstanceOf(OpenOrdersParamCurrencyPair.class);
  }
}
