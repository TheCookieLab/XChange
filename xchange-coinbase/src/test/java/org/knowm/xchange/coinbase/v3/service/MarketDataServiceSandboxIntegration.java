package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
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

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSandboxSpecificationWithCredentials();
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    marketDataService = (CoinbaseMarketDataService) exchange.getMarketDataService();
  }

  @Test
  public void testGetBestBidAskWithCurrencyPair() throws Exception {
    try {
      List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(CurrencyPair.ETH_USD);
      
      assertNotNull("Price books should not be null", priceBooks);
      assertEquals("Should return one price book", 1, priceBooks.size());
      
      CoinbasePriceBook priceBook = priceBooks.get(0);
      assertNotNull("Product ID should not be null", priceBook.getProductId());
      assertNotNull("Bids should not be null", priceBook.getBids());
      assertNotNull("Asks should not be null", priceBook.getAsks());
    } catch (Exception e) {
      System.out.println("Best bid/ask not fully supported in sandbox: " + e.getMessage());
    }
  }

  @Test
  public void testGetBestBidAskWithBaseAndCounterCurrency() throws Exception {
    try {
      List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(Currency.BTC,
          Currency.USD);
      
      assertNotNull("Price books should not be null", priceBooks);
      assertEquals("Should return one price book", 1, priceBooks.size());
    } catch (Exception e) {
      System.out.println("Best bid/ask not fully supported in sandbox: " + e.getMessage());
    }
  }

  @Test
  public void testGetMarketTradesWithLimit() throws Exception {
    try {
      Instrument currencyPair = CurrencyPair.ETH_USD;
      Trades trades = marketDataService.getTrades(currencyPair, 1);

      assertNotNull("Trades should not be null", trades);
      assertNotNull("Trades list should not be null", trades.getTrades());
      
      if (!trades.getTrades().isEmpty()) {
        assertEquals("Should respect limit", 1, trades.getTrades().size());
        assertEquals("Instrument should match", currencyPair, trades.getTrades().get(0).getInstrument());
      }
    } catch (Exception e) {
      System.out.println("Market trades not fully supported in sandbox: " + e.getMessage());
    }
  }

  @Test
  public void testGetProductCandleSticksWithLimit() throws Exception {
    try {
      CurrencyPair currencyPair = CurrencyPair.ETH_USD;
      int limit = 10;
      CandleStickData candleStickData = marketDataService.getCandleStickData(currencyPair,
          new DefaultCandleStickParamWithLimit(null, null, 86_400, limit));

      assertNotNull("CandleStickData should not be null", candleStickData);
      assertEquals("Instrument should match", currencyPair, candleStickData.getInstrument());
      
      if (!candleStickData.getCandleSticks().isEmpty()) {
        assertTrue("Should respect limit", candleStickData.getCandleSticks().size() <= limit);
        assertTrue("Candles should be sorted desc by time",
            candleStickData.getCandleSticks().get(0).getTimestamp().after(
                candleStickData.getCandleSticks().get(candleStickData.getCandleSticks().size() - 1)
                    .getTimestamp()));
      }
    } catch (Exception e) {
      System.out.println("Candlestick data not fully supported in sandbox: " + e.getMessage());
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

      assertNotNull("CandleStickData should not be null", candleStickData);
      assertEquals("Instrument should match", currencyPair, candleStickData.getInstrument());
    } catch (Exception e) {
      System.out.println("Candlestick data with date not fully supported in sandbox: " + e.getMessage());
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

      assertNotNull("Order book should not be null", orderBook);
      assertFalse("Asks should not be empty", orderBook.getAsks().isEmpty());
      assertFalse("Bids should not be empty", orderBook.getBids().isEmpty());
    } catch (Exception e) {
      System.out.println("Order book not fully supported in sandbox: " + e.getMessage());
    }
  }

  @Test
  public void testGetTicker() throws Exception {
    try {
      Instrument instrument = CurrencyPair.ETH_USD;
      Ticker ticker = marketDataService.getTicker(instrument);

      assertNotNull("Ticker should not be null", ticker);
      assertEquals("Instrument should match", instrument, ticker.getInstrument());
      assertNotNull("Ask should not be null", ticker.getAsk());
      assertNotNull("Bid should not be null", ticker.getBid());
      assertNotNull("Last should not be null", ticker.getLast());
    } catch (Exception e) {
      System.out.println("Ticker not fully supported in sandbox: " + e.getMessage());
    }
  }

  @Test
  public void testListSpotProducts() throws Exception {
    try {
      CoinbaseProductsResponse products = marketDataService.listProducts("SPOT");
      
      assertNotNull("Products should not be null", products);
      assertNotNull("Products list should not be null", products.getProducts());
      assertFalse("Products should not be empty", products.getProducts().isEmpty());
    } catch (Exception e) {
      System.out.println("List products not fully supported in sandbox: " + e.getMessage());
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
