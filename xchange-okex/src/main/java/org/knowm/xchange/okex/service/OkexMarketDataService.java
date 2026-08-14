package org.knowm.xchange.okex.service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.okex.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.service.OkxMarketDataService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.Params;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;

/**
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxMarketDataService} instead.
 */
@Deprecated
public class OkexMarketDataService extends OkexMarketDataServiceRaw implements MarketDataService {

  private final OkxMarketDataService delegate;

  public OkexMarketDataService(OkexExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.delegate = new OkxMarketDataService(exchange, resilienceRegistries);
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    try {
      return delegate.getOrderBook(instrument, args);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    try {
      return delegate.getTrades(instrument, args);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    try {
      return delegate.getTicker(instrument, args);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public CandleStickData getCandleStickData(CurrencyPair currencyPair, CandleStickDataParams params)
      throws IOException {
    try {
      return delegate.getCandleStickData(currencyPair, params);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public CandleStickData getCandleStickData(Instrument instrument, CandleStickDataParams params)
      throws IOException {
    try {
      return delegate.getCandleStickData(instrument, params);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public FundingRate getFundingRate(Instrument instrument) throws IOException {
    try {
      return delegate.getFundingRate(instrument);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public List<Ticker> getTickers(Params params) throws IOException {
    try {
      return delegate.getTickers(convertTickerParams(params));
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  /**
   * Converts legacy {@link OkexInstType} ticker parameters to their canonical {@link OkxInstType}
   * counterpart so the delegation reaches {@code OkxMarketDataService#getTickers} without tripping
   * its type guard.
   *
   * @param params the caller-supplied ticker parameters
   * @return the canonical parameters, or {@code params} unchanged when no conversion applies
   */
  static Params convertTickerParams(Params params) {
    return params instanceof OkexInstType ? ((OkexInstType) params).to() : params;
  }

  /**
   * Legacy-signature accessor returning the pre-rename element type {@link OkxFundingRateHistory}
   * (in the legacy {@code okex} package), so already-compiled callers keep receiving elements of
   * the original class.
   */
  public List<OkxFundingRateHistory> getFundingRateHistory(
      Instrument instrument, Long startTime, Long endTime, Integer limit) throws IOException {
    try {
      return delegate.getFundingRateHistory(instrument, startTime, endTime, limit).stream()
          .map(OkexMarketDataServiceRaw::toLegacyFundingRateHistory)
          .collect(Collectors.toList());
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }
}
