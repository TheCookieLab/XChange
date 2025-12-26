package org.knowm.xchange.examples.coinbase.marketdata;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseTimeResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseProductPriceBookResponse;
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

import java.io.IOException;
import java.util.List;

/**
 * Example demonstrating Coinbase Advanced Trade API v3 market data access.
 *
 * <p>This example shows two approaches:
 * <ul>
 *   <li>Using the XChange {@link MarketDataService} (no authentication required; falls back to
 *       public endpoints when unauthenticated)</li>
 *   <li>Using the public endpoints directly via a REST proxy</li>
 * </ul>
 *
 * <p>Public endpoints include:
 * <ul>
 *   <li>GET /api/v3/brokerage/time - Get Server Time</li>
 *   <li>GET /api/v3/brokerage/market/product_book - Get Public Product Book</li>
 *   <li>GET /api/v3/brokerage/market/products - List Public Products</li>
 *   <li>GET /api/v3/brokerage/market/products/{product_id} - Get Public Product</li>
 *   <li>GET /api/v3/brokerage/market/products/{product_id}/candles - Get Public Product Candles</li>
 *   <li>GET /api/v3/brokerage/market/products/{product_id}/ticker - Get Public Market Trades</li>
 * </ul>
 *
 * @author jamespedwards42
 */
public class CoinbaseMarketDataDemo {

    public static void main(String[] args) throws IOException {

        // Example 1: Using XChange MarketDataService (no auth required for market data)
        System.out.println("=== Using XChange MarketDataService (Public Fallback) ===");
        Exchange coinbaseExchange = CoinbaseDemoUtils.createExchangeWithoutAuth();
        MarketDataService marketDataService = coinbaseExchange.getMarketDataService();

        generic(marketDataService);
        raw((CoinbaseMarketDataService) marketDataService);

        // Example 2: Using public endpoints directly (no authentication required)
        System.out.println("\n=== Using Public Endpoints (Direct REST) ===");
        publicEndpoints();
    }

    /**
     * Demonstrates using public endpoints directly without authentication.
     * These endpoints work without API keys and are perfect for market data access.
     */
    private static void publicEndpoints() throws IOException {
        // Create exchange specification (no API keys needed for public endpoints)
        // Sandbox is preferred by default; override via -Dcoinbase.sandbox=false or COINBASE_SANDBOX=false.
        ExchangeSpecification spec = CoinbaseDemoUtils.createExchangeSpecification();

        // Build the public Coinbase interface proxy
        Coinbase coinbase = ExchangeRestProxyBuilder.forInterface(Coinbase.class, spec).build();

        // Get server time
        CoinbaseTimeResponse timeResponse = coinbase.getTime();
        System.out.println("Server Time: " + timeResponse.getIso());

        // Get public product book (order book)
        CoinbaseProductPriceBookResponse productBook = coinbase.getPublicProductBook("BTC-USD", 10, null);
        System.out.println("Product Book for BTC-USD:");
        System.out.println("  Best Bid: " + productBook.getPriceBook().getBids().get(0).getPrice());
        System.out.println("  Best Ask: " + productBook.getPriceBook().getAsks().get(0).getPrice());

        // Get public product information
        CoinbaseProductResponse product = coinbase.getPublicProduct("BTC-USD");
        System.out.println("Product Info for BTC-USD:");
        System.out.println("  Product ID: " + product.getProductId());
        System.out.println("  Price: " + product.getPrice());

        // List public products
        CoinbaseProductsResponse products = coinbase.listPublicProducts(10, null, "SPOT", null, null, null, null, null);
        System.out.println("Available SPOT Products (first 10):");
        products.getProducts().stream().limit(10).forEach(p -> System.out.println("  - " + p.getProductId()));

        // Get public product candles
        CoinbaseProductCandlesResponse candles = coinbase.getPublicProductCandles("BTC-USD", null, null, "ONE_HOUR", 10);
        System.out.println("Product Candles (last 10 hours):");
        candles.getCandles().forEach(c -> System.out.println("  Time: " + c.getStart() + ", Open: " + c.getOpen() + ", Close: " + c.getClose()));

        // Get public market trades
        CoinbaseProductMarketTradesResponse marketTrades = coinbase.getPublicMarketTrades("BTC-USD", 5, null, null);
        System.out.println("Recent Market Trades (last 5):");
        marketTrades.getMarketTrades().forEach(t -> System.out.println("  Trade ID: " + t.getTradeId() + ", Price: " + t.getPrice() + ", Size: " + t.getSize()));
    }

    /**
     * Demonstrates using the XChange service layer for market data.
     * This approach uses public endpoints when no credentials are configured and provides XChange DTOs.
     */
    private static void generic(MarketDataService marketDataService) throws IOException {

        Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
        System.out.println("Ticker: " + ticker);

        OrderBook orderBook = marketDataService.getOrderBook(CurrencyPair.BTC_USD, 10);
        System.out.println("Order Book: " + orderBook);

        Trades trades = marketDataService.getTrades(CurrencyPair.BTC_USD, 5);
        System.out.println("Recent Trades: " + trades);

        // 1 hour candles, limit 10
        CandleStickData candleStickData = marketDataService.getCandleStickData(
            CurrencyPair.BTC_USD,
            new DefaultCandleStickParamWithLimit(null, null, 3600L, 10));
        System.out.println("Candlestick Data: " + candleStickData);
    }

    /**
     * Demonstrates using the raw service methods for direct access to Coinbase DTOs.
     */
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
