package info.bitrich.xchangestream.binance;

import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.binance.BinanceExchange.EXCHANGE_TYPE;
import static org.knowm.xchange.binance.dto.ExchangeType.FUTURES;

import info.bitrich.xchangestream.binancefuture.BinanceFutureStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.util.Properties;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BinanceFutureManualExample {
  private static final Logger LOG = LoggerFactory.getLogger(BinanceFutureManualExample.class);
  static Instrument ETH = new FuturesContract("ETH/USDT/SWAP");
  static Instrument LTC = new FuturesContract("LTC/USDT/SWAP");

  public static void main(String[] args) throws InterruptedException {

    Properties properties = new Properties();
    try {
      properties.load(BinanceFutureManualExample.class.getResourceAsStream("/secret.keys"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    final String apiKey =
        (properties.getProperty("apikey") == null)
            ? System.getenv("binance-api-key")
            : properties.getProperty("apikey");
    final String apiSecret =
        (properties.getProperty("secret") == null)
            ? System.getenv("binance-api-secret")
            : properties.getProperty("secret");

    ExchangeSpecification spec =
        StreamingExchangeFactory.INSTANCE
            .createExchange(BinanceFutureStreamingExchange.class)
            .getDefaultExchangeSpecification();
    spec.setApiKey(apiKey);
    spec.setSecretKey(apiSecret);
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    spec.setExchangeSpecificParametersItem(EXCHANGE_TYPE, FUTURES);
    BinanceFutureStreamingExchange exchange =
        (BinanceFutureStreamingExchange) StreamingExchangeFactory.INSTANCE.createExchange(spec);

    ProductSubscription subscription =
        ProductSubscription.create()
            .addTicker(ETH)
            .addTicker(ETH)
            .addOrderbook(LTC)
            .addTrades(LTC)
            .addUserTrades(ETH)
            .addFundingRates(LTC)
            .build();

    exchange.connect(subscription).blockingAwait();
    exchange.enableLiveSubscription();

    LOG.info("Subscribing public channels");

    Disposable tickers =
        exchange
            .getStreamingMarketDataService()
            .getTicker(ETH)
            .subscribe(
                ticker -> LOG.info("Ticker: {}", ticker),
                throwable -> LOG.error("ERROR in getting ticker: ", throwable));

    Disposable trades =
        exchange
            .getStreamingMarketDataService()
            .getTrades(LTC)
            .subscribe(trade -> LOG.info("Trade: {}", trade));

    Disposable orderChanges = null;
    Disposable userTrades = null;
    Disposable balances = null;
    Disposable accountInfo = null;
    Disposable executionReports = null;

    if (apiKey != null) {

      LOG.info("Subscribing authenticated channels");

      // Level 1 (generic) APIs
      orderChanges =
          exchange
              .getStreamingTradeService()
              .getOrderChanges(true)
              .subscribe(oc -> LOG.info("Order change: {}", oc));
      userTrades =
          exchange
              .getStreamingTradeService()
              .getUserTrades(true)
              .subscribe(trade -> LOG.info("User trade: {}", trade));
      balances =
          exchange
              .getStreamingAccountService()
              .getBalanceChanges()
              .subscribe(
                  trade -> LOG.info("Balance: {}", trade),
                  e -> LOG.error("Error in balance stream", e));

      // Level 2 (exchange-specific) APIs
      executionReports =
          exchange
              .getStreamingTradeService()
              .getRawExecutionReports()
              .subscribe(report -> LOG.info("Subscriber got execution report: {}", report));
      accountInfo =
          exchange
              .getStreamingAccountService()
              .getRawAccountInfo()
              .subscribe(
                  accInfo ->
                      LOG.info(
                          "Subscriber got account Info (not printing, often causes console issues in IDEs)"));
    }

    Disposable orderbooks = orderbooks(exchange, "one");
    Thread.sleep(5000);
    Disposable orderbooks2 = orderbooks(exchange, "two");
    Disposable orderbookUpdates1 = orderbooksIncremental(exchange, "one");
    Disposable orderbookUpdates2 = orderbooksIncremental(exchange, "two");

    Thread.sleep(10000);

    tickers.dispose();
    trades.dispose();
    orderbooks.dispose();
    orderbooks2.dispose();
    orderbookUpdates1.dispose();
    orderbookUpdates2.dispose();

    if (apiKey != null) {
      orderChanges.dispose();
      userTrades.dispose();
      balances.dispose();
      accountInfo.dispose();
      executionReports.dispose();
    }

    exchange.disconnect().blockingAwait();
  }

  private static Disposable orderbooks(StreamingExchange exchange, String identifier) {
    return exchange
        .getStreamingMarketDataService()
        .getOrderBook(LTC)
        .subscribe(
            orderBook ->
                LOG.info(
                    "Order Book ({}): askDepth={} ask={} askSize={} bidDepth={}. bid={}, bidSize={}",
                    identifier,
                    orderBook.getAsks().size(),
                    orderBook.getAsks().get(0).getLimitPrice(),
                    orderBook.getAsks().get(0).getRemainingAmount(),
                    orderBook.getBids().size(),
                    orderBook.getBids().get(0).getLimitPrice(),
                    orderBook.getBids().get(0).getRemainingAmount()),
            throwable -> LOG.error("ERROR in getting order book: ", throwable));
  }

  private static Disposable orderbooksIncremental(StreamingExchange exchange, String identifier) {
    return exchange
        .getStreamingMarketDataService()
        .getOrderBookUpdates(LTC)
        .subscribe(
            level -> LOG.info("Order Book Level update({}): {}", identifier, level),
            throwable -> LOG.error("ERROR in getting order book: ", throwable));
  }

}
