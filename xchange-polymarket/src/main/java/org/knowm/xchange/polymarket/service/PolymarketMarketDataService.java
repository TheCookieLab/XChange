package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.service.marketdata.MarketDataService;

/** Generic market-data service for Polymarket prediction-market contracts. */
public class PolymarketMarketDataService extends PolymarketMarketDataServiceRaw
    implements MarketDataService {

  public PolymarketMarketDataService(PolymarketExchange exchange) {
    super(exchange);
  }

  /** Currency pairs are not Polymarket instruments; routed through validation for a clear error. */
  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    return PolymarketAdapters.adaptTicker(getBook(PolymarketAdapters.tokenId(instrument)));
  }

  /** Currency pairs are not Polymarket instruments; routed through validation for a clear error. */
  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return getOrderBook((Instrument) currencyPair, args);
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    return PolymarketAdapters.adaptOrderBook(getBook(PolymarketAdapters.tokenId(instrument)));
  }

  /** Currency pairs are not Polymarket instruments; routed through validation for a clear error. */
  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTrades((Instrument) currencyPair, args);
  }

  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    return PolymarketAdapters.adaptTrades(
        getDataTrades(PolymarketAdapters.conditionId(instrument), null));
  }
}
