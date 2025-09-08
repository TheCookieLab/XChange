package info.bitrich.xchangestream.bybit.example;

import static info.bitrich.xchangestream.bybit.example.BaseBybitExchange.connectDemoApi;
import static info.bitrich.xchangestream.bybit.example.BaseBybitExchange.connectMainApi;

import info.bitrich.xchangestream.bybit.BybitStreamingTradeService;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.trade.details.BybitHedgeMode;
import org.knowm.xchange.bybit.service.BybitAccountService;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BybitStreamOrderBookAndFeesExample {

  private static final Logger LOG =
      LoggerFactory.getLogger(BybitStreamOrderBookAndFeesExample.class);
  static Instrument instrument = new FuturesContract("XRP/USDT/PERP");

  public static void main(String[] args) {
    // main(not demo) api only
    websocketTradeExample();
    try {
      // Stream orderBook and OrderBookUpdates
      getOrderBookExample();
      getFeesExample();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    exchange.disconnect().blockingAwait();
  }

  private static void websocketTradeExample() {
    try {
      exchange = connectMainApi(BybitCategory.LINEAR, true);
      while (!exchange.isAlive()) {
        try {
          TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      // switch mode to two-way
      ((BybitAccountService) exchange.getAccountService()).switchPositionMode(BybitCategory.LINEAR, instrument, "USDT", 3);
      BigDecimal minAmountFuture =
          exchange.getExchangeMetaData().getInstruments().get(instrument).getMinimumAmount();
      MarketOrder marketOrder = new MarketOrder(OrderType.ASK, new BigDecimal("1"), instrument);
      marketOrder.addOrderFlag(BybitHedgeMode.TWOWAY);
      Disposable disposable = ((BybitStreamingTradeService) exchange.getStreamingTradeService()).placeMarketOrder(marketOrder)
          .subscribe(result -> {
                LOG.info("marketOrder is send, retCode: {}", result);
              }, throwable -> LOG.error("throwable", throwable));
      Thread.sleep(2000);
      LOG.info("is disposed: {}", disposable.isDisposed());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }


  static List<Disposable> booksDisposable = new ArrayList<>();
  static Instrument XRP_PERP = new FuturesContract("XRP/USDT/PERP");
  static StreamingExchange exchange;

  private static void getFeesExample() {
    exchange = connectMainApi(BybitCategory.LINEAR, true);
    // if auth response is not received at this moment, wee get non-auth exception here
    // var fees =
    // exchange.getAccountService().getDynamicTradingFeesByInstrument(BybitCategory.LINEAR);
    // OPTION - wait for login message response
    while (!exchange.isAlive()) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Map<Instrument, Fee> fees;
    try {
      fees =
          exchange
              .getAccountService()
              .getDynamicTradingFeesByInstrument(BybitCategory.LINEAR.getValue());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LOG.info("fees: {}", fees);
  }

  private static void getOrderBookExample() throws InterruptedException {
    exchange = connectDemoApi(BybitCategory.LINEAR, false);
    subscribeOrderBook();
    Thread.sleep(6000L);
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
