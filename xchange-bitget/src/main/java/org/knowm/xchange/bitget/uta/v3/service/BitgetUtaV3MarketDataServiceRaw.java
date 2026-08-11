package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ErrorAdapter;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Candle;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Instrument;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3OrderBook;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3ServerTime;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Ticker;

/** Raw UTA v3 public market data calls. */
public class BitgetUtaV3MarketDataServiceRaw extends BitgetUtaV3BaseService {

  public BitgetUtaV3MarketDataServiceRaw(BitgetExchange exchange) {
    super(exchange);
  }

  public BitgetUtaV3ServerTime getServerTime() throws IOException {
    try {
      return bitgetUtaV3.serverTime().getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  public List<BitgetUtaV3Instrument> getInstruments(BitgetUtaV3Category category, String symbol)
      throws IOException {
    try {
      return bitgetUtaV3.instruments(category.getWireName(), symbol).getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  public BitgetUtaV3OrderBook getOrderBook(
      String symbol, BitgetUtaV3Category category, Integer limit) throws IOException {
    try {
      return bitgetUtaV3.orderbook(symbol, category.getWireName(), limit).getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  public List<BitgetUtaV3Ticker> getTickers(BitgetUtaV3Category category, String symbol)
      throws IOException {
    try {
      return bitgetUtaV3.tickers(category.getWireName(), symbol).getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  public List<BitgetUtaV3Candle> getCandles(
      String symbol,
      BitgetUtaV3Category category,
      String interval,
      Integer limit,
      String startTime,
      String endTime)
      throws IOException {
    try {
      return bitgetUtaV3
          .candles(symbol, category.getWireName(), interval, limit, startTime, endTime)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }
}
