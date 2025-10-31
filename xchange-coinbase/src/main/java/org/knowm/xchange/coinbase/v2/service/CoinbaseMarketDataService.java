package org.knowm.xchange.coinbase.v2.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;

/**
 * Market data service for Coinbase v2 API.
 *
 * @deprecated The Coinbase v2 API provides retail pricing but lacks trading functionality. Use
 *     {@link org.knowm.xchange.coinbase.v3.service.CoinbaseMarketDataService} instead for full
 *     market data via the Coinbase Advanced Trade API.
 */
@Deprecated
public class CoinbaseMarketDataService extends CoinbaseMarketDataServiceRaw
    implements MarketDataService {

  public CoinbaseMarketDataService(Exchange exchange) {

    super(exchange);
  }

  @Override
  public Ticker getTicker(CurrencyPair pair, final Object... args) throws IOException {

    throw new NotAvailableFromExchangeException();
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, final Object... args) {

    throw new NotAvailableFromExchangeException();
  }

  @Override
  public Trades getTrades(CurrencyPair currencyPair, final Object... args) {

    throw new NotAvailableFromExchangeException();
  }
}
