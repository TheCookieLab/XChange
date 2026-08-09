package org.knowm.xchange.uniswap.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.ExchangeHealth;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.Params;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapQuote;

/**
 * Standard XChange market data over the raw quoting service.
 *
 * <p>The ticker is derived from a configured reference-size quote in both directions, both
 * simulated at the same captured block: {@code ask} is the exact-input price of the reference base
 * amount and {@code bid} is the exact-output price of the reference base amount.
 */
public class UniswapMarketDataService extends UniswapMarketDataServiceRaw implements MarketDataService {

  public UniswapMarketDataService(UniswapExchange exchange) {
    super(exchange);
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    if (!(instrument instanceof CurrencyPair)) {
      throw new NotAvailableFromExchangeException("only currency pairs are supported: " + instrument);
    }
    CurrencyPair pair = (CurrencyPair) instrument;
    if (exchange.getConfig().pools().byPair(pair) == null) {
      throw new NotAvailableFromExchangeException("no configured v4 pool for " + pair);
    }
    try {
      BigInteger block = getLatestBlock();
      UniswapQuote askQuote = quoteExactInput(pair, exchange.getConfig().quoteRefSize(), block);
      UniswapQuote bidQuote = quoteExactOutput(pair, exchange.getConfig().quoteRefSize(), block);
      BigDecimal ask = askQuote.price();
      BigDecimal bid = bidQuote.price();
      if (ask == null || bid == null) {
        throw new ExchangeException("quoter returned a zero amount for " + pair);
      }
      return new Ticker.Builder()
          .instrument(pair)
          .bid(bid)
          .ask(ask)
          .timestamp(Date.from(bidQuote.quotedAt()))
          .build();
    } catch (IOException e) {
      throw new ExchangeException("failed to quote " + pair + ": " + e.getMessage(), e);
    }
  }

  @Override
  public List<Ticker> getTickers(Params params) {
    throw new NotAvailableFromExchangeException("getTickers");
  }

  @Override
  public ExchangeHealth getExchangeHealth() {
    try {
      exchange.getNodeClient().blockNumber();
      return ExchangeHealth.ONLINE;
    } catch (IOException e) {
      return ExchangeHealth.OFFLINE;
    }
  }
}
