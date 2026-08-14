package org.knowm.xchange.examples.okx.v5.marketdata;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.service.marketdata.MarketDataService;

public class OkxDepthDemo {

  public static void main(String[] args) throws IOException {

    ExchangeSpecification exSpec = new ExchangeSpecification(OkxExchange.class);
    Exchange okxExchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    generic(okxExchange);
  }

  private static void generic(Exchange okxExchange) throws IOException {

    // Interested in the public market data feed (no authentication)
    MarketDataService marketDataService = okxExchange.getMarketDataService();

    FuturesContract contract = new FuturesContract(CurrencyPair.BTC_USDT, "SWAP");

    // Get the latest full order book data for BTC/USDT Perpetual Swap
    OrderBook orderBook = marketDataService.getOrderBook(contract);
    System.out.println(orderBook.toString());
    System.out.println(
        "full orderbook size: " + (orderBook.getAsks().size() + orderBook.getBids().size()));
  }
}
