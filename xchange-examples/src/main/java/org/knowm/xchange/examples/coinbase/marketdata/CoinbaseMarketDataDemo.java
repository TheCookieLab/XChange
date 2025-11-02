package org.knowm.xchange.examples.coinbase.marketdata;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductsResponse;
import org.knowm.xchange.coinbase.v3.service.CoinbaseMarketDataService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.examples.coinbase.CoinbaseDemoUtils;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;

/**
 * @deprecated This example class is deprecated. For code examples and usage, refer to:
 * <ul>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.MarketDataServiceIntegration MarketDataServiceIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.MarketDataServiceSandboxIntegration MarketDataServiceSandboxIntegration}</li>
 * </ul>
 * @author jamespedwards42
 */
@SuppressWarnings("JavadocReference")
@Deprecated
public class CoinbaseMarketDataDemo {

  public static void main(String[] args) throws IOException {

    Exchange coinbaseExchange = CoinbaseDemoUtils.createExchange();
    MarketDataService marketDataService = coinbaseExchange.getMarketDataService();

    generic(marketDataService);
    raw((CoinbaseMarketDataService) marketDataService);
  }

  private static void generic(MarketDataService marketDataService) throws IOException {

    Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
    System.out.println("Ticker: " + ticker);

    OrderBook orderBook = marketDataService.getOrderBook(CurrencyPair.BTC_USD, 10);
    System.out.println("Order Book: " + orderBook);

    Trades trades = marketDataService.getTrades(CurrencyPair.BTC_USD, 5);
    System.out.println("Recent Trades: " + trades);

    CandleStickData candleStickData = marketDataService.getCandleStickData(CurrencyPair.BTC_USD,
        new DefaultCandleStickParamWithLimit(null, null, 3600L, 10)); // 1 hour candles, limit 10
    System.out.println("Candlestick Data: " + candleStickData);
  }

  private static void raw(CoinbaseMarketDataService marketDataService) throws IOException {

    // Get product information
    CoinbaseProductResponse product = marketDataService.getProduct("BTC-USD");
    System.out.println("Product Info: " + product);

    // Get best bid/ask prices
    List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(CurrencyPair.BTC_USD);
    System.out.println("Best Bid/Ask: " + priceBooks);

    // Get market trades
    CoinbaseProductMarketTradesResponse marketTrades = marketDataService.getMarketTrades("BTC-USD", 5, null, null);
    System.out.println("Market Trades: " + marketTrades);

    // Get product candles
    CoinbaseProductCandlesResponse candles = marketDataService.getProductCandles("BTC-USD", "ONE_HOUR", 10, null, null);
    System.out.println("Product Candles: " + candles);

    // List products
    try {
      CoinbaseProductsResponse products = marketDataService.listProducts("SPOT");
      System.out.println("Available Products (first 10): " + products.getProducts().subList(0, Math.min(10, products.getProducts().size())));
    } catch (Exception e) {
      System.out.println("List products not available: " + e.getMessage());
    }
  }
}
