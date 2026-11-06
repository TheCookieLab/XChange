package org.knowm.xchange.coinbase.v3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseTestUtils;
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

/**
 * Integration tests for {@link CoinbaseMarketDataService} using Coinbase sandbox environment.
 * 
 * <p><b>Note:</b> Coinbase sandbox primarily supports Accounts and Orders endpoints.
 * Market data endpoints may have limited or no support. These tests attempt to exercise
 * market data functionality and document what works in the sandbox.
 * 
 * <p><b>Sandbox URL:</b> https://api-sandbox.coinbase.com
 * 
 * <p><b>Endpoints Tested (availability varies):</b>
 * <ul>
 *   <li>GET /api/v3/brokerage/best_bid_ask - Best bid/ask prices</li>
 *   <li>GET /api/v3/brokerage/products/{product_id}/ticker - Market trades</li>
 *   <li>GET /api/v3/brokerage/products/{product_id}/candles - Candlestick data</li>
 *   <li>GET /api/v3/brokerage/product_book - Order book</li>
 *   <li>GET /api/v3/brokerage/products/{product_id} - Product details</li>
 *   <li>GET /api/v3/brokerage/products - List products</li>
 * </ul>
 * 
 * <p><b>Usage:</b>
 * <pre>
 * mvn test -Dtest=MarketDataServiceSandboxIntegration
 * </pre>
 * 
 * @see <a href="https://docs.cdp.coinbase.com/coinbase-business/advanced-trade-apis/sandbox">Coinbase Sandbox Docs</a>
 */
public class MarketDataServiceSandboxIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseMarketDataService marketDataService;

  @BeforeAll
  public static void beforeClass() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSandboxSpecificationWithCredentials();
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    marketDataService = (CoinbaseMarketDataService) exchange.getMarketDataService();
  }

  @Test
  public void testGetBestBidAskWithCurrencyPair() throws Exception {
    try {
      List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(CurrencyPair.ETH_USD);
      
      assertNotNull(priceBooks, "Price books should not be null");
      assertEquals(1, priceBooks.size(), "Should return one price book");
      
      CoinbasePriceBook priceBook = priceBooks.get(0);
      assertNotNull(priceBook.getProductId(), "Product ID should not be null");
      assertNotNull(priceBook.getBids(), "Bids should not be null");
      assertNotNull(priceBook.getAsks(), "Asks should not be null");
    } catch (Exception e) {
      System.out.println("Best bid/ask not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetBestBidAskWithBaseAndCounterCurrency() throws Exception {
    try {
      List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(Currency.BTC,
          Currency.USD);
      
      assertNotNull(priceBooks, "Price books should not be null");
      assertEquals(1, priceBooks.size(), "Should return one price book");
    } catch (Exception e) {
      System.out.println("Best bid/ask not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetMarketTradesWithLimit() throws Exception {
    try {
      Instrument currencyPair = CurrencyPair.ETH_USD;
      Trades trades = marketDataService.getTrades(currencyPair, 1);

      assertNotNull(trades, "Trades should not be null");
      assertNotNull(trades.getTrades(), "Trades list should not be null");
      
      if (!trades.getTrades().isEmpty()) {
        assertEquals(1, trades.getTrades().size(), "Should respect limit");
        assertEquals(currencyPair, trades.getTrades().get(0).getInstrument(), "Instrument should match");
      }
    } catch (Exception e) {
      System.out.println("Market trades not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetProductCandleSticksWithLimit() throws Exception {
    try {
      CurrencyPair currencyPair = CurrencyPair.ETH_USD;
      int limit = 10;
      CandleStickData candleStickData = marketDataService.getCandleStickData(currencyPair,
          new DefaultCandleStickParamWithLimit(null, null, 86_400, limit));

      assertNotNull(candleStickData, "CandleStickData should not be null");
      assertEquals(currencyPair, candleStickData.getInstrument(), "Instrument should match");
      
      if (!candleStickData.getCandleSticks().isEmpty()) {
        assertTrue(candleStickData.getCandleSticks().size() <= limit, "Should respect limit");
        assertTrue(candleStickData.getCandleSticks().get(0).getTimestamp().isAfter(
            candleStickData.getCandleSticks().get(candleStickData.getCandleSticks().size() - 1)
                .getTimestamp()), "Candles should be sorted desc by time");
      }
    } catch (Exception e) {
      System.out.println("Candlestick data not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetProductCandleSticksWithStartDate() throws Exception {
    try {
      CurrencyPair currencyPair = CurrencyPair.ETH_USD;
      int daysInPast = 30;
      Date startDate = Date.from(
          LocalDate.now().minusDays(daysInPast).atStartOfDay(ZoneOffset.UTC).toInstant());
      CandleStickData candleStickData = marketDataService.getCandleStickData(currencyPair,
          new DefaultCandleStickParam(startDate, null, 86_400));

      assertNotNull(candleStickData, "CandleStickData should not be null");
      assertEquals(currencyPair, candleStickData.getInstrument(), "Instrument should match");
    } catch (Exception e) {
      System.out.println("Candlestick data with date not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetOrderBookWithLimitAndPriceAggregation() throws Exception {
    try {
      Instrument instrument = CurrencyPair.ETH_USD;
      double priceAggregationIncrement = 1;
      int limit = 20;

      OrderBook orderBook = marketDataService.getOrderBook(instrument, limit,
          priceAggregationIncrement);

      assertNotNull(orderBook, "Order book should not be null");
      assertFalse(orderBook.getAsks().isEmpty(), "Asks should not be empty");
      assertFalse(orderBook.getBids().isEmpty(), "Bids should not be empty");
    } catch (Exception e) {
      System.out.println("Order book not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetTicker() throws Exception {
    try {
      Instrument instrument = CurrencyPair.ETH_USD;
      Ticker ticker = marketDataService.getTicker(instrument);

      assertNotNull(ticker, "Ticker should not be null");
      assertEquals(instrument, ticker.getInstrument(), "Instrument should match");
      assertNotNull(ticker.getAsk(), "Ask should not be null");
      assertNotNull(ticker.getBid(), "Bid should not be null");
      assertNotNull(ticker.getLast(), "Last should not be null");
    } catch (Exception e) {
      System.out.println("Ticker not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testListSpotProducts() throws Exception {
    try {
      CoinbaseProductsResponse products = marketDataService.listProducts("SPOT");
      
      assertNotNull(products, "Products should not be null");
      assertNotNull(products.getProducts(), "Products list should not be null");
      assertFalse(products.getProducts().isEmpty(), "Products should not be empty");
    } catch (Exception e) {
      System.out.println("List products not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testSandboxCapabilitiesSummary() {
    System.out.println("\n=== Coinbase Sandbox Market Data Capabilities ===");
    
    testEndpoint("Best Bid/Ask (currency pair)", () -> 
        marketDataService.getBestBidAsk(CurrencyPair.BTC_USD));
    
    testEndpoint("Best Bid/Ask (base/counter)", () -> 
        marketDataService.getBestBidAsk(Currency.BTC, Currency.USD));
    
    testEndpoint("Market Trades", () -> 
        marketDataService.getTrades(CurrencyPair.BTC_USD, 5));
    
    testEndpoint("Candlestick Data", () -> 
        marketDataService.getCandleStickData(CurrencyPair.BTC_USD,
            new DefaultCandleStickParamWithLimit(null, null, 86400, 5)));
    
    testEndpoint("Order Book", () -> 
        marketDataService.getOrderBook(CurrencyPair.BTC_USD, 10));
    
    testEndpoint("Ticker", () -> 
        marketDataService.getTicker(CurrencyPair.BTC_USD));
    
    testEndpoint("List Products", () -> 
        marketDataService.listProducts("SPOT"));
    
    System.out.println("=================================================\n");
  }

  private void testEndpoint(String name, TestRunnable test) {
    try {
      test.run();
      System.out.println("✓ " + name + ": SUPPORTED");
    } catch (Exception e) {
      System.out.println("✗ " + name + ": NOT SUPPORTED (" + e.getClass().getSimpleName() + ")");
    }
  }

  @FunctionalInterface
  private interface TestRunnable {
    void run() throws Exception;
  }
}
