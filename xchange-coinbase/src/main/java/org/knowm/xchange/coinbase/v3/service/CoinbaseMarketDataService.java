package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBooksResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;

public class CoinbaseMarketDataService extends CoinbaseMarketDataServiceRaw
    implements MarketDataService {

  public CoinbaseMarketDataService(Exchange exchange) {
    super(exchange);
  }

  public List<CoinbasePriceBook> getBestBidAsk(Currency base, Currency counter) throws IOException {
    CurrencyPair currencyPair = new CurrencyPair(base, counter);

    return this.getBestBidAsk(currencyPair);
  }

  public List<CoinbasePriceBook> getBestBidAsk(CurrencyPair currencyPair) throws IOException {
    return this.getBestBidAsk(formatProductId(currencyPair)).getPriceBooks();
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
  public Trades getTrades(CurrencyPair currencyPair, final Object... args) throws IOException {
    CoinbaseProductMarketTradesResponse response = this.getMarketTrades(formatProductId(currencyPair), null, null, null);
  }

  private static String formatProductId(CurrencyPair currencyPair) {
    Objects.requireNonNull(currencyPair, "Cannot format productId from a null currencyPair");
    return currencyPair.toString().replace("/", "-");
  }
}
