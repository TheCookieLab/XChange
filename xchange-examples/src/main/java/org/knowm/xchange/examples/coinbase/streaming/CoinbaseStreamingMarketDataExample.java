package org.knowm.xchange.examples.coinbase.streaming;

import info.bitrich.xchangestream.coinbase.CoinbaseCandleGranularity;
import info.bitrich.xchangestream.coinbase.CoinbaseStreamingExchange;
import info.bitrich.xchangestream.coinbase.CoinbaseStreamingMarketDataService;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Demonstrates how to connect to Coinbase Advanced Trade streaming APIs using the {@code
 * xchange-stream-coinbase} module. The example subscribes to ticker, trades, order book, and
 * candle updates for BTC/USD and logs incoming events to standard out.
 *
 * <p>Run against sandbox (default):
 *
 * <pre>{@code
 * mvn -pl xchange-examples -am \
 *   org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *   -Dexec.mainClass=org.knowm.xchange.examples.coinbase.streaming.CoinbaseStreamingMarketDataExample
 * }</pre>
 *
 * <p>Run against production:
 *
 * <pre>{@code
 * mvn -pl xchange-examples -am \
 *   org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *   -Dexec.mainClass=org.knowm.xchange.examples.coinbase.streaming.CoinbaseStreamingMarketDataExample \
 *   -Dcoinbase.streaming.sandbox=false
 * }</pre>
 *
 * <p>The process will keep running until interrupted (Ctrl+C). When stopping, subscriptions and the
 * WebSocket connection are shut down gracefully.
 */
public final class CoinbaseStreamingMarketDataExample {

  private static final CurrencyPair PAIR = CurrencyPair.BTC_USD;

  private CoinbaseStreamingMarketDataExample() {}

  public static void main(String[] args) throws InterruptedException {
    StreamingExchange exchange =
        StreamingExchangeFactory.INSTANCE.createExchange(CoinbaseStreamingExchange.class);

    ExchangeSpecification specification = exchange.getExchangeSpecification();
    if (useSandbox()) {
      specification.setExchangeSpecificParametersItem(
          CoinbaseStreamingExchange.PARAM_SANDBOX, true);
      System.out.println("Connecting to Coinbase sandbox WebSocket...");
    } else {
      System.out.println("Connecting to Coinbase production WebSocket...");
    }
    exchange.applySpecification(specification);

    ProductSubscription subscription =
        ProductSubscription.create()
            .addTicker(PAIR)
            .addTrades(PAIR)
            .addOrderbook(PAIR)
            .build();

    exchange.connect(subscription).timeout(30, TimeUnit.SECONDS).blockingAwait();
    System.out.println("Connected. Subscribed channels: ticker, trades, order book, candles.");

    CoinbaseStreamingMarketDataService marketDataService =
        (CoinbaseStreamingMarketDataService) exchange.getStreamingMarketDataService();

    Disposable tickerSubscription =
        marketDataService
            .getTicker(PAIR)
            .subscribe(
                ticker -> System.out.println("Ticker: " + ticker),
                error -> logError("ticker", error));

    Disposable tradesSubscription =
        marketDataService
            .getTrades(PAIR)
            .subscribe(
                trade -> System.out.println("Trade: " + trade),
                error -> logError("trades", error));

    Disposable orderBookSubscription =
        marketDataService
            .getOrderBook(PAIR)
            .subscribe(
                orderBook -> System.out.println("Order book: " + summarizeBook(orderBook)),
                error -> logError("order book", error));

    Disposable candleSubscription =
        marketDataService
            .getCandles(PAIR, CoinbaseCandleGranularity.ONE_MINUTE)
            .subscribe(
                candle -> System.out.println("Candle: " + candle),
                error -> logError("candles", error));

    List<Disposable> disposables =
        Arrays.asList(tickerSubscription, tradesSubscription, orderBookSubscription, candleSubscription);
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

  private static boolean useSandbox() {
    String value = readOptional("coinbase.streaming.sandbox", "COINBASE_STREAMING_SANDBOX");
    if (value == null) {
      value = readOptional("coinbase.sandbox", "COINBASE_SANDBOX");
    }
    if (value == null) {
      return true;
    }
    return parseFlag(value);
  }

  private static boolean parseFlag(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase();
    return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
  }

  private static String readOptional(String propertyKey, String envKey) {
    String value = System.getProperty(propertyKey);
    if (value == null || value.trim().isEmpty()) {
      value = System.getenv(envKey);
    }
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }

  private static void logError(String channel, Throwable error) {
    System.err.printf("Error from %s channel: %s%n", channel, error.getMessage());
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
