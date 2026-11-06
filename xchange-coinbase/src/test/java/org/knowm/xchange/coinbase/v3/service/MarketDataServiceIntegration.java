package org.knowm.xchange.coinbase.v3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductsResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
import org.knowm.xchange.utils.AuthUtils;

public class MarketDataServiceIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseMarketDataService marketDataService;

  @BeforeAll
  public static void beforeClass() {
    ExchangeSpecification exchangeSpecification = ExchangeFactory.INSTANCE.createExchange(
        CoinbaseExchange.class).getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exchangeSpecification);
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    marketDataService = (CoinbaseMarketDataService) exchange.getMarketDataService();
  }

  @Test
  public void getBestBidAskWithCurrencyPair() throws Exception {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(CurrencyPair.ETH_USD);
    assertEquals(1, priceBooks.size());
  }

  @Test
  public void getBestBidAskWithBaseAndCounterCurrency() throws Exception {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(Currency.BTC,
        Currency.USD);
    assertEquals(1, priceBooks.size());
  }

  @Test
  public void getMarketTradesWithLimit() throws Exception {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    Instrument currencyPair = CurrencyPair.ETH_USD;
    Trades trades = marketDataService.getTrades(currencyPair, 1);

    assertEquals(1, trades.getTrades().size());
    assertEquals(currencyPair, trades.getTrades().get(0).getInstrument());
  }

  @Test
  public void getETHUSDProductCandleSticksWithLimit() throws Exception {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    CurrencyPair currencyPair = CurrencyPair.ETH_USD;
    int limit = 10;
    CandleStickData candleStickData = marketDataService.getCandleStickData(currencyPair,
        new DefaultCandleStickParamWithLimit(null, null, 86_400, limit));

    assertEquals(limit, candleStickData.getCandleSticks().size());
    assertEquals(currencyPair, candleStickData.getInstrument());
    assertTrue(candleStickData.getCandleSticks().get(0).getTimestamp().isAfter(
        candleStickData.getCandleSticks().get(candleStickData.getCandleSticks().size() - 1)
            .getTimestamp()));
  }

  @Test
  public void getETHUSDProductCandleSticksWithStartDate() throws Exception {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    CurrencyPair currencyPair = CurrencyPair.ETH_USD;
    int daysInPast = 100;
    Date startDate = Date.from(
        LocalDate.now().minusDays(daysInPast).atStartOfDay(ZoneOffset.UTC).toInstant());
    CandleStickData candleStickData = marketDataService.getCandleStickData(currencyPair,
        new DefaultCandleStickParam(startDate, null, 86_400));

    int observedSize = candleStickData.getCandleSticks().size();
    assertTrue(observedSize >= daysInPast && observedSize <= daysInPast + 2, "Candlestick count should be close to the requested day window");
    assertEquals(currencyPair, candleStickData.getInstrument());
    assertTrue(candleStickData.getCandleSticks().get(0).getTimestamp().isAfter(
        candleStickData.getCandleSticks().get(candleStickData.getCandleSticks().size() - 1)
            .getTimestamp()));
  }

  @Test
  public void getETHUSDOrderBookWithLimitAndPriceAggregationIncrement() throws IOException {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    Instrument instrument = CurrencyPair.ETH_USD;
    double priceAggregationIncrement = 1;
    int limit = 20;

    OrderBook orderBook = marketDataService.getOrderBook(instrument, limit,
        priceAggregationIncrement);

    assertFalse(orderBook.getAsks().isEmpty());
    assertFalse(orderBook.getBids().isEmpty());
  }

  @Test
  public void getETHUSDTicker() throws IOException {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    Instrument instrument = CurrencyPair.ETH_USD;

    Ticker ticker = marketDataService.getTicker(instrument);

    assertEquals(instrument, ticker.getInstrument());
    assertNotNull(ticker.getAsk());
    assertNotNull(ticker.getAskSize());
    assertNotNull(ticker.getBid());
    assertNotNull(ticker.getBidSize());

    assertNotNull(ticker.getPercentageChange());
    assertNotNull(ticker.getQuoteVolume());
    assertNotNull(ticker.getVolume());

    assertNotNull(ticker.getOpen());
    assertNotNull(ticker.getHigh());
    assertNotNull(ticker.getLow());
    assertNotNull(ticker.getLast());
  }

  @Test
  public void listSpotProducts() throws Exception {
    Assumptions.assumeTrue(marketDataService.authTokenCreator != null);

    CoinbaseProductsResponse products = marketDataService.listProducts("SPOT");

    assertFalse(products.getProducts().isEmpty());
  }
}
