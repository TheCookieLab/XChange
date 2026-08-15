package org.knowm.xchange.mexc.v3.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.mexc.v3.MexcV3Symbols;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.client.ReplaySafety;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3AggTrade;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3AvgPrice;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3BookTicker;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3DefaultSymbols;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Depth;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3ExchangeInfo;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Kline;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3KlineInterval;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3PriceTicker;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3ServerTime;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Ticker24h;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Trade;

/** Raw (DTO-level) access to the public MEXC Spot v3 market-data endpoints. */
public class MexcV3MarketDataServiceRaw extends MexcV3BaseService {

  protected MexcV3MarketDataServiceRaw(Exchange exchange) {
    super(exchange);
  }

  /** Test connectivity. */
  public String ping() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.ping(), ReplaySafety.READ);
  }

  /** Server time in Unix milliseconds. */
  public long getServerTime() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.time(), ReplaySafety.READ).getServerTime();
  }

  /** Full exchange information (all symbols). */
  public MexcV3ExchangeInfo getExchangeInfo() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.exchangeInfo(null, null), ReplaySafety.READ);
  }

  /** Exchange information for one symbol. */
  public MexcV3ExchangeInfo getExchangeInfo(CurrencyPair pair)
      throws IOException, MexcV3Exception {
    return execute(
        () -> mexcV3MarketData.exchangeInfo(MexcV3Symbols.toMexcSymbol(pair), null),
        ReplaySafety.READ);
  }

  /** Order book snapshot; {@code limit} defaults to 100, max 5000. */
  public MexcV3Depth getDepth(CurrencyPair pair, Integer limit)
      throws IOException, MexcV3Exception {
    return execute(
        () -> mexcV3MarketData.depth(MexcV3Symbols.toMexcSymbol(pair), limit), ReplaySafety.READ);
  }

  /** Recent public trades; {@code limit} defaults to 500, max 1000. */
  public List<MexcV3Trade> getTrades(CurrencyPair pair, Integer limit)
      throws IOException, MexcV3Exception {
    return execute(
        () -> mexcV3MarketData.trades(MexcV3Symbols.toMexcSymbol(pair), limit), ReplaySafety.READ);
  }

  /** Aggregated public trades; {@code startTime}/{@code endTime} are inclusive, required together. */
  public List<MexcV3AggTrade> getAggTrades(
      CurrencyPair pair, Long startTime, Long endTime, Integer limit)
      throws IOException, MexcV3Exception {
    return execute(
        () ->
            mexcV3MarketData.aggTrades(
                MexcV3Symbols.toMexcSymbol(pair), startTime, endTime, limit),
        ReplaySafety.READ);
  }

  /** Klines; {@code limit} defaults to 500, max 500. */
  public List<MexcV3Kline> getKlines(
      CurrencyPair pair, MexcV3KlineInterval interval, Long startTime, Long endTime, Integer limit)
      throws IOException, MexcV3Exception {
    return execute(
        () ->
            mexcV3MarketData.klines(
                MexcV3Symbols.toMexcSymbol(pair),
                interval.getWireValue(),
                startTime,
                endTime,
                limit),
        ReplaySafety.READ);
  }

  /** Current average price over the provider's window. */
  public MexcV3AvgPrice getAvgPrice(CurrencyPair pair) throws IOException, MexcV3Exception {
    return execute(
        () -> mexcV3MarketData.avgPrice(MexcV3Symbols.toMexcSymbol(pair)), ReplaySafety.READ);
  }

  /** 24-hour rolling ticker for one symbol. */
  public MexcV3Ticker24h getTicker24h(CurrencyPair pair) throws IOException, MexcV3Exception {
    return execute(
        () -> mexcV3MarketData.ticker24h(MexcV3Symbols.toMexcSymbol(pair)), ReplaySafety.READ);
  }

  /** 24-hour rolling tickers for every symbol. */
  public List<MexcV3Ticker24h> getTicker24hAll() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.ticker24hAll(), ReplaySafety.READ);
  }

  /** Symbol price ticker for one symbol. */
  public MexcV3PriceTicker getPriceTicker(CurrencyPair pair) throws IOException, MexcV3Exception {
    return execute(
        () -> mexcV3MarketData.priceTicker(MexcV3Symbols.toMexcSymbol(pair)), ReplaySafety.READ);
  }

  /** Symbol price tickers for every symbol. */
  public List<MexcV3PriceTicker> getPriceTickerAll() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.priceTickerAll(), ReplaySafety.READ);
  }

  /** Order book ticker (best bid/ask) for one symbol. */
  public MexcV3BookTicker getBookTicker(CurrencyPair pair) throws IOException, MexcV3Exception {
    return execute(
        () -> mexcV3MarketData.bookTicker(MexcV3Symbols.toMexcSymbol(pair)), ReplaySafety.READ);
  }

  /** Order book tickers for every symbol. */
  public List<MexcV3BookTicker> getBookTickerAll() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.bookTickerAll(), ReplaySafety.READ);
  }

  /** Server time DTO (raw payload). */
  public MexcV3ServerTime getServerTimeDto() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.time(), ReplaySafety.READ);
  }

  /** Provider default symbols (weight 1 envelope endpoint). */
  public MexcV3DefaultSymbols defaultSymbols() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3MarketData.defaultSymbols(), ReplaySafety.READ);
  }
}
