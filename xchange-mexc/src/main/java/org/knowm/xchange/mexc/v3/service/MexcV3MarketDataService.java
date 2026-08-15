package org.knowm.xchange.mexc.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.ExchangeHealth;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.mexc.v3.MexcV3Adapters;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3KlineInterval;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Ticker24h;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.Params;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;
import org.knowm.xchange.service.trade.params.CurrencyPairParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;

/** High-level market-data service over the MEXC Spot v3 public REST surface. */
public class MexcV3MarketDataService extends MexcV3MarketDataServiceRaw
    implements MarketDataService {

  public MexcV3MarketDataService(Exchange exchange) {
    super(exchange);
  }

  @Override
  @Deprecated
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    return MexcV3Adapters.adaptTicker24h(getTicker24h(toPair(instrument)), toPair(instrument));
  }

  @Override
  public List<Ticker> getTickers(Params params) throws IOException {
    List<MexcV3Ticker24h> raw =
        params instanceof CurrencyPairParam && ((CurrencyPairParam) params).getCurrencyPair() != null
            ? List.of(getTicker24h(((CurrencyPairParam) params).getCurrencyPair()))
            : getTicker24hAll();
    List<Ticker> tickers = new ArrayList<>(raw.size());
    for (MexcV3Ticker24h ticker : raw) {
      tickers.add(
          MexcV3Adapters.adaptTicker24h(
              ticker, org.knowm.xchange.mexc.v3.MexcV3Symbols.toCurrencyPair(ticker.getSymbol())));
    }
    return tickers;
  }

  @Override
  @Deprecated
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return getOrderBook((Instrument) currencyPair, args);
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    Integer limit = null;
    if (args != null && args.length > 0 && args[0] instanceof Integer) {
      limit = (Integer) args[0];
    }
    return MexcV3Adapters.adaptOrderBook(getDepth(toPair(instrument), limit), toPair(instrument));
  }

  @Override
  @Deprecated
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTrades((Instrument) currencyPair, args);
  }

  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    Integer limit = null;
    if (args != null && args.length > 0 && args[0] instanceof Integer) {
      limit = (Integer) args[0];
    }
    return MexcV3Adapters.adaptTrades(getTrades(toPair(instrument), limit), toPair(instrument));
  }

  @Override
  @Deprecated
  public CandleStickData getCandleStickData(
      CurrencyPair currencyPair, CandleStickDataParams params) throws IOException {
    return getCandleStickData((Instrument) currencyPair, params);
  }

  @Override
  public CandleStickData getCandleStickData(Instrument instrument, CandleStickDataParams params)
      throws IOException {
    CurrencyPair pair = toPair(instrument);
    if (!(params instanceof DefaultCandleStickParam)) {
      throw new ExchangeException(
          "MEXC Spot v3 requires DefaultCandleStickParam(WithLimit) candle params");
    }
    DefaultCandleStickParam candleParams = (DefaultCandleStickParam) params;
    MexcV3KlineInterval interval = intervalForPeriod(candleParams.getPeriodInSecs());
    Long startTime = candleParams.getStartDate() == null ? null : candleParams.getStartDate().getTime();
    Long endTime = candleParams.getEndDate() == null ? null : candleParams.getEndDate().getTime();
    Integer limit = null;
    if (candleParams instanceof DefaultCandleStickParamWithLimit) {
      limit = ((DefaultCandleStickParamWithLimit) candleParams).getLimit();
    }
    List<CandleStick> sticks =
        MexcV3Adapters.adaptKlines(
            getKlines(pair, interval, startTime, endTime, limit), pair);
    return new CandleStickData(pair, sticks);
  }

  @Override
  public ExchangeHealth getExchangeHealth() {
    try {
      ping();
      return ExchangeHealth.ONLINE;
    } catch (IOException | MexcV3Exception | ExchangeException e) {
      return ExchangeHealth.OFFLINE;
    }
  }

  /** Maps a period in seconds to the closest MEXC kline interval. */
  private static MexcV3KlineInterval intervalForPeriod(long periodInSecs) {
    switch ((int) periodInSecs) {
      case 60:
        return MexcV3KlineInterval.M1;
      case 300:
        return MexcV3KlineInterval.M5;
      case 900:
        return MexcV3KlineInterval.M15;
      case 1800:
        return MexcV3KlineInterval.M30;
      case 3600:
        return MexcV3KlineInterval.M60;
      case 14400:
        return MexcV3KlineInterval.H4;
      case 86400:
        return MexcV3KlineInterval.D1;
      case 604800:
        return MexcV3KlineInterval.WEEK1;
      case 2592000:
        return MexcV3KlineInterval.MONTH1;
      default:
        throw new ExchangeException(
            "Unsupported MEXC Spot v3 kline period in seconds: " + periodInSecs);
    }
  }

  private static CurrencyPair toPair(Instrument instrument) {
    if (!(instrument instanceof CurrencyPair)) {
      throw new ExchangeException(
          "MEXC Spot v3 supports currency pairs only, got " + instrument);
    }
    return (CurrencyPair) instrument;
  }
}
