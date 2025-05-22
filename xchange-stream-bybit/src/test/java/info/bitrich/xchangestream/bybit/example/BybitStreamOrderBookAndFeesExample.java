package info.bitrich.xchangestream.bybit.example;

import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.var;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static info.bitrich.xchangestream.bybit.example.BaseBybitExchange.connect;

public class BybitStreamOrderBookAndFeesExample {

  private static final Logger LOG = LoggerFactory.getLogger(BybitStreamOrderBookAndFeesExample.class);

  public static void main(String[] args) {
    // Stream orderBook and OrderBookUpdates
    try {
      getOrderBookExample();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  static List<Disposable> booksDisposable = new ArrayList<>();
  static Instrument XRP_PERP = new FuturesContract("XRP/USDT/PERP");
  static StreamingExchange exchange;

  private static void getFeesExample() {
    exchange = connect(BybitCategory.LINEAR, true);
    // if auth response is not received at this moment, wee get non-auth exception here
    // var fees = exchange.getAccountService().getDynamicTradingFeesByInstrument(BybitCategory.LINEAR);
    // OPTION - wait for login message response
    while(!exchange.isAlive()) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    var fees = exchange.getAccountService().getDynamicTradingFeesByInstrument(BybitCategory.LINEAR);
    LOG.info("fees: {}", fees);
  }

  private static void getOrderBookExample() throws InterruptedException {
    exchange = connect(BybitCategory.LINEAR, false);
    subscribeOrderBook();
    Thread.sleep(600000L);
    for (Disposable dis : booksDisposable) {
      dis.dispose();
    }
    exchange.disconnect().blockingAwait();
  }

  private static void subscribeOrderBook() {
    booksDisposable.add(
        exchange
            .getStreamingMarketDataService()
            .getOrderBook(XRP_PERP)
            .doOnError(
                error -> {
                  LOG.error(error.getMessage());
                  for (Disposable dis : booksDisposable) {
                    dis.dispose();
                  }
                  subscribeOrderBook();
                })
            .subscribe(
                orderBook -> System.out.print("."),
                throwable -> {
                  LOG.error(throwable.getMessage());
                }));
  }
}
