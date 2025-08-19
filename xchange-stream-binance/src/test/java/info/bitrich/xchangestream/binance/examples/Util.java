package info.bitrich.xchangestream.binance.examples;

import org.knowm.xchange.dto.marketdata.OrderBook;

public class Util {
  static String printOrderBookShortInfo(OrderBook orderBook) {
    return String.format("orderBook subscribe: askDepth=%s ask=%s askSize=%s bidDepth=%s. bid=%s, bidSize=%s",
    orderBook.getAsks().size(),
        orderBook.getAsks().get(0).getLimitPrice(),
        orderBook.getAsks().get(0).getRemainingAmount(),
        orderBook.getBids().size(),
        orderBook.getBids().get(0).getLimitPrice(),
        orderBook.getBids().get(0).getRemainingAmount());
  }

}
