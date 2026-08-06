package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataTrade;
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

  /**
   * Recent public trades for the requested outcome contract. The Data API filters by condition id
   * and returns rows for both outcome tokens of the condition, so rows are post-filtered by the
   * requested contract's outcome id; every returned trade's instrument matches the request (see
   * https://docs.polymarket.com/api-reference/core/get-trades-for-a-user-or-markets).
   */
  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    String tokenId = PolymarketAdapters.tokenId(instrument);
    List<PolymarketDataTrade> rows =
        getDataTrades(PolymarketAdapters.conditionId(instrument), null);
    List<PolymarketDataTrade> matching =
        rows.stream().filter(trade -> tokenId.equals(trade.asset())).toList();
    return PolymarketAdapters.adaptTrades(matching);
  }
}
