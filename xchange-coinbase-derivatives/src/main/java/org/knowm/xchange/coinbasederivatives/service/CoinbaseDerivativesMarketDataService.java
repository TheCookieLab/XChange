package org.knowm.xchange.coinbasederivatives.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesAdapters;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;

/** Generic XChange market-data facade. */
public class CoinbaseDerivativesMarketDataService extends CoinbaseDerivativesMarketDataServiceRaw
    implements MarketDataService {
  public CoinbaseDerivativesMarketDataService(CoinbaseDerivativesExchange exchange) {
    super(exchange);
  }

  public List<Instrument> getInstruments() throws IOException {
    return getInstruments("any", null, false).stream()
        .filter(CoinbaseDerivativesInstrument::active)
        .map(CoinbaseDerivativesAdapters::registerInstrument)
        .toList();
  }

  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTicker(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    return CoinbaseDerivativesAdapters.adaptTicker(
        getTicker(CoinbaseDerivativesAdapters.toNativeName(instrument)));
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return getOrderBook(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    Integer depth = args.length > 0 && args[0] instanceof Integer value ? value : null;
    return CoinbaseDerivativesAdapters.adaptOrderBook(
        getOrderBook(CoinbaseDerivativesAdapters.toNativeName(instrument), depth));
  }

  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTrades(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    Integer count = args.length > 0 && args[0] instanceof Integer value ? value : null;
    return new Trades(
        getLastTrades(CoinbaseDerivativesAdapters.toNativeName(instrument), count, null)
            .trades()
            .stream()
            .map(CoinbaseDerivativesAdapters::adaptTrade)
            .toList());
  }
}
