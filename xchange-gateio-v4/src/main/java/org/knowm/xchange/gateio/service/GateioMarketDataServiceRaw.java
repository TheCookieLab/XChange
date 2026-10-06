package org.knowm.xchange.gateio.service;

import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.gateio.GateioAdapters;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.gateio.dto.marketdata.*;
import org.knowm.xchange.instrument.Instrument;

import java.io.IOException;
import java.util.List;

public class GateioMarketDataServiceRaw extends GateioBaseService {

  public GateioMarketDataServiceRaw(GateioExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public GateioServerTime getGateioServerTime() throws IOException {
    return gateio.getServerTime();
  }

  public List<GateioTicker> getGateioTickers(Instrument instrument) throws IOException {
    return gateio.getTickers(GateioAdapters.toGateioInstrument(instrument));
  }

  public List<GateioFuturesTickerAndFunding> getGateioFuturesTickers(Instrument instrument)
      throws IOException {
    String settle = "usdt";
    return gateio.getFuturesTickers(settle, GateioAdapters.toGateioInstrument(instrument));
  }

  public List<GateioCurrencyInfo> getGateioCurrencyInfos() throws IOException {
    return gateio.getCurrencies();
  }

  public GateioCurrencyInfo getGateioCurrencyInfo(Currency currency) throws IOException {
    return gateio.getCurrency(currency.getCurrencyCode());
  }

  public List<GateioTrade> getGateioTrades(
      Instrument instrument, Integer limit, String lastId, Long from, Long to)
      throws IOException {
    return gateio.getTrades(
        GateioAdapters.toGateioInstrument(instrument), limit, lastId, null, from, to, null);
  }

  public List<GateioCandleStick> getGateioCandlesticks(
      Instrument instrument, String interval, Integer limit, Long from, Long to)
      throws IOException {
    return gateio.getCandlesticks(GateioAdapters.toGateioInstrument(instrument), limit, from, to, interval)
        .stream()
        .map(GateioCandleStick::fromRow)
        .collect(Collectors.toList());
  }

  public GateioOrderBook getGateioOrderBook(Instrument instrument) throws IOException {
    return gateio.getOrderBook(GateioAdapters.toGateioInstrument(instrument), false);
  }

  public List<GateioCurrencyChain> getCurrencyChains(Currency currency) throws IOException {
    return gateio.getCurrencyChains(currency.getCurrencyCode());
  }

  public List<GateioCurrencyPairDetails> getCurrencyPairDetails() throws IOException {
    return gateio.getCurrencyPairDetails();
  }

  public List<GateioInstrumentDetails> getInstrumentDetails() throws IOException {
    return gateio.getInstrumentDetails();
  }

  public GateioCurrencyPairDetails getCurrencyPairDetails(Instrument instrument)
      throws IOException {
    return gateio.getCurrencyPairDetails(GateioAdapters.toGateioInstrument(instrument));
  }

  public List<GateioSpotCandlestick> getGateioSpotCandlesticks(
      Instrument instrument, Integer limit, Long from, Long to, String interval)
      throws IOException {
    return gateio.getSpotCandlesticks(
        GateioAdapters.toGateioInstrument(instrument), limit, from, to, interval);
  }

  public List<GateioFuturesCandlestick> getGateioFuturesCandlesticks(
      Instrument instrument, Integer limit, Long from, Long to, String interval)
      throws IOException {
    return gateio.getFuturesCandlesticks(
        instrument.getCounter().toString().toLowerCase(),
        GateioAdapters.toGateioInstrument(instrument),
        limit,
        from,
        to,
        interval);
  }

  public List<GateioFundingRateHistory> getGateioFundingRateHistory(
      Instrument instrument, Long startTime, Long endTime, Integer limit)
      throws IOException {
    return gateio.getFundingRateHistory(
        instrument.getCounter().toString().toLowerCase(),
        GateioAdapters.toGateioInstrument(instrument),
        limit,
        startTime,
        endTime);
  }
}
