package org.knowm.xchange.okx.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxCandleStick;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.Params;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxMarketDataService extends OkxMarketDataServiceRaw implements MarketDataService {

  public OkxMarketDataService(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    return OkxAdapters.adaptOrderBook(
        getOkxOrderbook(OkxAdapters.adaptInstrument(instrument)),
        instrument,
        exchange.getExchangeMetaData());
  }

  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    return OkxAdapters.adaptTrades(
        getOkxTrades(OkxAdapters.adaptInstrument(instrument), 100).getData(),
        instrument,
        exchange.getExchangeMetaData());
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    return OkxAdapters.adaptTicker(
        getOkxTicker(OkxAdapters.adaptInstrument(instrument)).getData().get(0));
  }

  @Override
  public CandleStickData getCandleStickData(
      CurrencyPair currencyPair, CandleStickDataParams params) throws IOException {
    return getCandleStickData((Instrument) currencyPair, params);
  }

  @Override
  public CandleStickData getCandleStickData(Instrument instrument, CandleStickDataParams params)
      throws IOException {

    if (!(params instanceof DefaultCandleStickParam)) {
      throw new NotYetImplementedForExchangeException("Only DefaultCandleStickParam is supported");
    }
    DefaultCandleStickParam defaultCandleStickParam = (DefaultCandleStickParam) params;
    OkxCandleStickPeriodType periodType =
        OkxCandleStickPeriodType.getPeriodTypeFromSecs(defaultCandleStickParam.getPeriodInSecs());
    if (periodType == null) {
      throw new NotYetImplementedForExchangeException(
          "Only discrete period values are supported;"
              + Arrays.toString(OkxCandleStickPeriodType.getSupportedPeriodsInSecs()));
    }

    String limit = null;
    if (params instanceof DefaultCandleStickParamWithLimit) {
      limit = String.valueOf(((DefaultCandleStickParamWithLimit) params).getLimit());
    }

    OkxResponse<List<OkxCandleStick>> historyCandle =
        getHistoryCandle(
            OkxAdapters.adaptInstrument(instrument),
            String.valueOf(defaultCandleStickParam.getEndDate().getTime()),
            String.valueOf(defaultCandleStickParam.getStartDate().getTime()),
            periodType.getFieldValue(),
            limit);
    return OkxAdapters.adaptCandleStickData(historyCandle.getData(), instrument);
  }

  @Override
  public FundingRate getFundingRate(Instrument instrument) throws IOException {
    return OkxAdapters.adaptFundingRate(
        getOkxFundingRate(OkxAdapters.adaptInstrument(instrument)).getData());
  }

  public List<Ticker> getTickers(Params params) throws IOException {
    if (!(params instanceof OkxInstType)) {
      throw new IllegalArgumentException("Params must be instance of OkxInstType");
    }
    OkxInstType instType = (OkxInstType) params;
    return getOkxTickers(instType).getData().stream()
        .map(OkxAdapters::adaptTicker)
        .collect(Collectors.toList());
  }

  public List<OkxFundingRateHistory> getFundingRateHistory(
      Instrument instrument, Long startTime, Long endTime, Integer limit) throws IOException {
    List<OkxFundingRateHistory> result =
        getOkxFundingRateHistoryRaw(
            OkxAdapters.adaptInstrument(instrument), startTime, endTime, limit);
    // sort, oldest first
    result.sort(Comparator.comparingLong(c -> c.getFundingTime().toEpochMilli()));
    return result;
  }
}
