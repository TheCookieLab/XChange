package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kalshi.KalshiAdapters;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.service.marketdata.MarketDataService;

/** Generic market-data service for Kalshi prediction-market contracts. */
public class KalshiMarketDataService extends KalshiMarketDataServiceRaw
    implements MarketDataService {

  public KalshiMarketDataService(KalshiExchange exchange) {
    super(exchange);
  }

  /** Currency pairs are not Kalshi instruments; routed through validation for a clear error. */
  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    return KalshiAdapters.adaptTicker(
        getKalshiMarket(KalshiAdapters.marketTicker(instrument)).market());
  }

  /** Currency pairs are not Kalshi instruments; routed through validation for a clear error. */
  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return getOrderBook((Instrument) currencyPair, args);
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    Integer depth = args != null && args.length > 0 && args[0] instanceof Number n
        ? n.intValue()
        : null;
    String ticker = KalshiAdapters.marketTicker(instrument);
    return KalshiAdapters.adaptOrderBook(ticker, getKalshiOrderBook(ticker, depth));
  }

  /** Currency pairs are not Kalshi instruments; routed through validation for a clear error. */
  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTrades((Instrument) currencyPair, args);
  }

  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    String ticker = KalshiAdapters.marketTicker(instrument);
    return KalshiAdapters.adaptTrades(getKalshiTrades(ticker, null, null).trades());
  }
}
