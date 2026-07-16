package org.knowm.xchange.coinbasederivatives.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesChartData;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesOrderBook;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesTicker;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesTrades;

/** Exchange-specific public market-data operations. */
public class CoinbaseDerivativesMarketDataServiceRaw extends CoinbaseDerivativesBaseService {
  public CoinbaseDerivativesMarketDataServiceRaw(CoinbaseDerivativesExchange exchange) {
    super(exchange);
  }

  public List<CoinbaseDerivativesInstrument> getInstruments(
      String currency, String kind, Boolean expired) throws IOException {
    Map<String, Object> params = new ConcurrentHashMap<>();
    put(params, "currency", currency);
    put(params, "kind", kind);
    put(params, "expired", expired);
    return Arrays.asList(
        transport.callPublic(
            "public/get_instruments", params, CoinbaseDerivativesInstrument[].class));
  }

  public CoinbaseDerivativesTicker getTicker(String instrumentName) throws IOException {
    return transport.callPublic(
        "public/ticker",
        Map.of("instrument_name", instrumentName),
        CoinbaseDerivativesTicker.class);
  }

  public CoinbaseDerivativesOrderBook getOrderBook(String instrumentName, Integer depth)
      throws IOException {
    Map<String, Object> params = new ConcurrentHashMap<>();
    params.put("instrument_name", instrumentName);
    put(params, "depth", depth);
    return transport.callPublic(
        "public/get_order_book", params, CoinbaseDerivativesOrderBook.class);
  }

  public CoinbaseDerivativesTrades getLastTrades(
      String instrumentName, Integer count, String sorting) throws IOException {
    Map<String, Object> params = new ConcurrentHashMap<>();
    params.put("instrument_name", instrumentName);
    put(params, "count", count);
    put(params, "sorting", sorting);
    return transport.callPublic(
        "public/get_last_trades_by_instrument", params, CoinbaseDerivativesTrades.class);
  }

  public CoinbaseDerivativesChartData getChartData(
      String instrumentName, long startTimestamp, long endTimestamp, String resolution)
      throws IOException {
    return transport.callPublic(
        "public/get_tradingview_chart_data",
        Map.of(
            "instrument_name", instrumentName,
            "start_timestamp", startTimestamp,
            "end_timestamp", endTimestamp,
            "resolution", resolution),
        CoinbaseDerivativesChartData.class);
  }

  private static void put(Map<String, Object> params, String name, Object value) {
    if (value != null) {
      params.put(name, value);
    }
  }
}
