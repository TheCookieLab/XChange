package org.knowm.xchange.examples.coinbase.streaming;

import info.bitrich.xchangestream.coinbase.CoinbaseStreamingMarketDataService;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.examples.coinbase.CoinbaseDemoUtils;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Demonstrates how to connect to Coinbase Advanced Trade streaming APIs using the {@code
 * xchange-stream-coinbase} module. The example subscribes to order book and trades for ETH/USD
 * and logs incoming events to standard out.
 *
 * <p>Note: There is no sandbox environment for WebSocket connections. This example always connects
 * to the production Coinbase Advanced Trade WebSocket endpoint.
 *
 * <p>Run the example:
 *
 * <pre>{@code
 * mvn -pl xchange-examples -am \
 *   org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *   -Dexec.mainClass=org.knowm.xchange.examples.coinbase.streaming.CoinbaseStreamingMarketDataExample
 * }</pre>
 *
 * <p>Optional arguments:
 * <ul>
 *   <li>First argument: "user" to use user order data endpoint, otherwise uses market data endpoint</li>
 *   <li>Second argument: "level2", "level2_batch", or "l2_data" to specify order book channel (default: "level2")</li>
 * </ul>
 *
 * <p>The process will keep running until interrupted (Ctrl+C). When stopping, subscriptions and the
 * WebSocket connection are shut down gracefully.
 */
public final class CoinbaseStreamingMarketDataExample {

  private static final CurrencyPair PAIR = CurrencyPair.ETH_USD;

  private CoinbaseStreamingMarketDataExample() {}

  public static void main(String[] args) throws InterruptedException {
    // Determine which endpoint to use
    boolean useUserEndpoint = args.length > 0 && "user".equalsIgnoreCase(args[0]);
    
    StreamingExchange exchange = useUserEndpoint 
        ? CoinbaseDemoUtils.createStreamingExchangeForUserOrderData()
        : CoinbaseDemoUtils.createStreamingExchangeForMarketData();
    
    String endpoint = useUserEndpoint ? "user order data" : "market data";
    System.out.println("Connecting to Coinbase Advanced Trade WebSocket (" + endpoint + " endpoint)...");

    // Connect to initialize services
    ProductSubscription subscription = ProductSubscription.create().build();
    exchange.connect(subscription).timeout(30, TimeUnit.SECONDS).blockingAwait();
    System.out.println("Connected. Exchange is alive: " + exchange.isAlive());
    
    // Monitor connection state changes
    exchange.connectionStateObservable()
        .subscribe(state -> System.out.println("Connection state: " + state));
    
    // Monitor reconnection failures
    exchange.reconnectFailure()
        .subscribe(error -> {
          System.err.println("Reconnection failure: " + error.getMessage());
          error.printStackTrace();
        });
    
    // Give the connection a moment to stabilize
    Thread.sleep(1000);

    CoinbaseStreamingMarketDataService marketDataService =
        (CoinbaseStreamingMarketDataService) exchange.getStreamingMarketDataService();

    // Subscribe to order book
    // Use level2_batch by default (batches updates every 0.05s, recommended for high-volume products)
    String channelArg = args.length > 1 ? args[1] : "level2";
    io.reactivex.rxjava3.core.Observable<OrderBook> orderBookObservable;
    
    String websocketUrl = exchange.getExchangeSpecification().getOverrideWebsocketApiUri();
    if (websocketUrl == null) {
      websocketUrl = info.bitrich.xchangestream.coinbase.CoinbaseStreamingExchange.PROD_WS_URI;
    }
    
    if ("level2_batch".equalsIgnoreCase(channelArg)) {
      System.out.println("Subscribing to order book for " + PAIR + " using level2_batch channel on " + websocketUrl);
      System.out.println("(level2_batch batches updates every 0.05s - recommended for high-volume products)");
      orderBookObservable = marketDataService.getOrderBookBatch(PAIR);
    } else if ("l2_data".equalsIgnoreCase(channelArg)) {
      System.out.println("Subscribing to order book for " + PAIR + " using l2_data channel on " + websocketUrl);
      orderBookObservable = marketDataService.getOrderBook(PAIR, "l2_data");
    } else {
      System.out.println("Subscribing to order book for " + PAIR + " using level2 channel on " + websocketUrl);
      orderBookObservable = marketDataService.getOrderBook(PAIR);
    }
    
    Disposable orderBookSubscription = orderBookObservable
        .subscribe(
            orderBook -> {
              if (orderBook != null && (!orderBook.getBids().isEmpty() || !orderBook.getAsks().isEmpty())) {
                System.out.println("Order book update: " + summarizeBook(orderBook));
              }
            },
            error -> {
              System.err.println("Order book error: " + error.getMessage());
              error.printStackTrace();
            });

    // Subscribe to trades
    Disposable tradesSubscription = marketDataService
        .getTrades(PAIR)
        .subscribe(
            trade -> System.out.println("Trade: " + trade),
            error -> System.err.println("Trades error: " + error.getMessage()));

    List<Disposable> disposables = Arrays.asList(orderBookSubscription, tradesSubscription);
    CountDownLatch shutdown = new CountDownLatch(1);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("\nShutting down Coinbase streaming example...");
                  disposables.forEach(
                      disposable -> {
                        if (disposable != null && !disposable.isDisposed()) {
                          disposable.dispose();
                        }
                      });
                  exchange
                      .disconnect()
                      .timeout(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS)
                      .blockingAwait();
                  shutdown.countDown();
                }));

    System.out.println("Press Ctrl+C to stop.");
    shutdown.await();
  }

  private static String summarizeBook(OrderBook orderBook) {
    LimitOrder bestBid =
        orderBook.getBids().isEmpty() ? null : orderBook.getBids().get(0);
    LimitOrder bestAsk =
        orderBook.getAsks().isEmpty() ? null : orderBook.getAsks().get(0);
    return String.format(
        "bestBid=%s/%s bestAsk=%s/%s depth=%d|%d",
        bestBid == null ? "n/a" : bestBid.getLimitPrice(),
        bestBid == null ? "n/a" : bestBid.getOriginalAmount(),
        bestAsk == null ? "n/a" : bestAsk.getLimitPrice(),
        bestAsk == null ? "n/a" : bestAsk.getOriginalAmount(),
        orderBook.getBids().size(),
        orderBook.getAsks().size());
  }
}
