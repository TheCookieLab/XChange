package org.knowm.xchange.dase.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dase.DaseAdapters;
import org.knowm.xchange.dase.dto.marketdata.DaseTrade;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.service.marketdata.MarketDataService;

public class DaseMarketDataService extends DaseMarketDataServiceRaw implements MarketDataService {

  public DaseMarketDataService(Exchange exchange) {
    super(exchange);
  }

  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return DaseAdapters.adaptTicker(
        getTicker(DaseAdapters.toMarketString(currencyPair)), currencyPair);
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return DaseAdapters.adaptOrderBook(
        getSnapshot(DaseAdapters.toMarketString(currencyPair)), currencyPair);
  }

  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    List<DaseTrade> raw = getTrades(DaseAdapters.toMarketString(currencyPair), null, null);
    return DaseAdapters.adaptTrades(raw, currencyPair);
  }
}
