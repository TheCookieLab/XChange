package org.knowm.xchange.okex.service;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.marketdata.OkexCandleStick;
import org.knowm.xchange.okex.dto.marketdata.OkexCurrency;
import org.knowm.xchange.okex.dto.marketdata.OkexFundingRate;
import org.knowm.xchange.okex.dto.marketdata.OkexFundingRateHistory;
import org.knowm.xchange.okex.dto.marketdata.OkexInstrument;
import org.knowm.xchange.okex.dto.marketdata.OkexOrderbook;
import org.knowm.xchange.okex.dto.marketdata.OkexTicker;
import org.knowm.xchange.okex.dto.marketdata.OkexTrade;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.okx.service.OkxBaseService;
import org.knowm.xchange.okx.service.OkxMarketDataServiceRaw;

/**
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxMarketDataServiceRaw} instead.
 */
@Deprecated
public class OkexMarketDataServiceRaw extends OkxBaseService {

  private final OkxMarketDataServiceRaw delegate;

  public OkexMarketDataServiceRaw(
      OkexExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.delegate = new OkxMarketDataServiceRaw(exchange, resilienceRegistries);
  }

  private static <S, T> OkexResponse<List<T>> wrap(
      OkxResponse<List<S>> response, Function<S, T> mapper) {
    return new OkexResponse<>(
        new OkxResponse<>(
            response.getId(),
            response.getCode(),
            response.getMsg(),
            response.getData().stream().map(mapper).collect(Collectors.toList())));
  }

  public OkexResponse<List<OkexInstrument>> getOkexInstruments(
      String instrumentType, String underlying, String instrumentId)
      throws OkexException, IOException {
    try {
      return wrap(
          delegate.getOkxInstruments(instrumentType, underlying, instrumentId), OkexInstrument::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexTicker>> getOkexTicker(String instrumentId)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxTicker(instrumentId), OkexTicker::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexTicker>> getOkexTickers(OkexInstType instType)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxTickers(instType.to()), OkexTicker::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexFundingRate>> getOkexFundingRate(String instrumentId)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxFundingRate(instrumentId), OkexFundingRate::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexCurrency>> getOkexCurrencies() throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxCurrencies(), OkexCurrency::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<String>> getOkexUnderlyings(OkexInstType instType)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxUnderlyings(instType.to()), s -> s);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexTrade>> getOkexTrades(String instrument, int limit)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxTrades(instrument, limit), OkexTrade::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexOrderbook>> getOkexOrderbook(String instrument)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxOrderbook(instrument), OkexOrderbook::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexCandleStick>> getHistoryCandle(
      String instrument, String after, String before, String bar, String limit)
      throws OkexException, IOException {
    try {
      return wrap(
          delegate.getHistoryCandle(instrument, after, before, bar, limit), OkexCandleStick::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexCandleStick>> getCandle(
      String instrument, String after, String before, String bar, String limit)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getCandle(instrument, after, before, bar, limit), OkexCandleStick::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public List<OkexFundingRateHistory> getOkexFundingRateHistoryRaw(
      String instrument, Long startTime, Long endTime, Integer limit) throws IOException {
    return delegate
        .getOkxFundingRateHistoryRaw(instrument, startTime, endTime, limit)
        .stream()
        .map(OkexFundingRateHistory::new)
        .collect(Collectors.toList());
  }

  public List<OkxFundingRateHistory> getOkxFundingRateHistoryRaw(
      String instrument, Long startTime, Long endTime, Integer limit) throws IOException {
    return delegate.getOkxFundingRateHistoryRaw(instrument, startTime, endTime, limit);
  }
}
