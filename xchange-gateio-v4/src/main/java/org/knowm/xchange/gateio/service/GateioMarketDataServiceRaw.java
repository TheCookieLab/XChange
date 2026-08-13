package org.knowm.xchange.gateio.service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.gateio.GateioAdapters;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.gateio.dto.marketdata.GateioCandleStick;
import org.knowm.xchange.gateio.dto.marketdata.GateioCurrencyChain;
import org.knowm.xchange.gateio.dto.marketdata.GateioCurrencyInfo;
import org.knowm.xchange.gateio.dto.marketdata.GateioCurrencyPairDetails;
import org.knowm.xchange.gateio.dto.marketdata.GateioOrderBook;
import org.knowm.xchange.gateio.dto.marketdata.GateioServerTime;
import org.knowm.xchange.gateio.dto.marketdata.GateioTicker;
import org.knowm.xchange.gateio.dto.marketdata.GateioTrade;
import org.knowm.xchange.instrument.Instrument;

public class GateioMarketDataServiceRaw extends GateioBaseService {

  public GateioMarketDataServiceRaw(GateioExchange exchange) {
    super(exchange);
  }

  public GateioServerTime getGateioServerTime() throws IOException {
    return gateio.getServerTime();
  }

  public List<GateioTicker> getGateioTickers(Instrument instrument) throws IOException {
    return gateio.getTickers(GateioAdapters.toString(instrument));
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
        GateioAdapters.toString(instrument), limit, lastId, null, from, to, null);
  }

  public List<GateioCandleStick> getGateioCandlesticks(
      Instrument instrument, String interval, Integer limit, Long from, Long to)
      throws IOException {
    return gateio.getCandlesticks(GateioAdapters.toString(instrument), limit, from, to, interval)
        .stream()
        .map(GateioCandleStick::fromRow)
        .collect(Collectors.toList());
  }

  public GateioOrderBook getGateioOrderBook(Instrument instrument) throws IOException {
    return gateio.getOrderBook(GateioAdapters.toString(instrument), false);
  }

  public List<GateioCurrencyChain> getCurrencyChains(Currency currency) throws IOException {
    return gateio.getCurrencyChains(currency.getCurrencyCode());
  }

  public List<GateioCurrencyPairDetails> getCurrencyPairDetails() throws IOException {
    return gateio.getCurrencyPairDetails();
  }

  public GateioCurrencyPairDetails getCurrencyPairDetails(Instrument instrument)
      throws IOException {
    return gateio.getCurrencyPairDetails(GateioAdapters.toString(instrument));
  }
}
