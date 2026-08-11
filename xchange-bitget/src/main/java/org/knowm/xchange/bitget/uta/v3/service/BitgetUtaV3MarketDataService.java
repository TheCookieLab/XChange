package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3Adapters;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ErrorAdapter;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Instrument;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3OrderBook;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Ticker;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.Params;

/**
 * UTA v3 market data service.
 *
 * <p>In addition to the standard {@link MarketDataService} surface, exposes {@link
 * #buildExchangeMetaData()} used by {@code BitgetExchange#remoteInitUtaV3()}: it discovers products
 * across the spot, margin, usdt-futures, coin-futures and usdc-futures categories and builds one
 * metadata entry per distinct XChange instrument. Margin shares the spot instrument universe (both
 * map to the same {@link CurrencyPair}), so margin rows never collide with spot rows; futures use
 * {@link org.knowm.xchange.derivative.FuturesContract} keys that preserve the derivative identity.
 */
public class BitgetUtaV3MarketDataService extends BitgetUtaV3MarketDataServiceRaw
    implements MarketDataService {

  public BitgetUtaV3MarketDataService(BitgetExchange exchange) {
    super(exchange);
  }

  /**
   * Discovers all products and builds {@link ExchangeMetaData}.
   *
   * <p>Spot and margin instruments share symbol text; margin is represented by its spot twin (see
   * class javadoc). Only instruments currently {@code online} are included, and Reality (simulated)
   * pairs are excluded from the tradeable universe.
   */
  public ExchangeMetaData buildExchangeMetaData() throws IOException {
    Map<Instrument, InstrumentMetaData> instruments = new ConcurrentHashMap<>();
    try {
      for (BitgetUtaV3Category category : BitgetUtaV3Category.values()) {
        if (category == BitgetUtaV3Category.MARGIN) {
          continue; // represented by spot twins; avoids equal-symbol collisions
        }
        List<BitgetUtaV3Instrument> rows = getInstruments(category, null);
        for (BitgetUtaV3Instrument row : rows) {
          if (!"online".equals(row.getStatus()) || "yes".equalsIgnoreCase(row.getIsReality())) {
            continue;
          }
          Instrument instrument = BitgetUtaV3Adapters.toInstrument(row);
          if (!instruments.containsKey(instrument)) {
            instruments.put(instrument, toInstrumentMetaData(row));
          }
        }
      }
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
    return new ExchangeMetaData(instruments, null, null, null, null);
  }

  private InstrumentMetaData toInstrumentMetaData(BitgetUtaV3Instrument row) {
    return InstrumentMetaData.builder()
        .minimumAmount(row.getMinOrderQty())
        .maximumAmount(row.getMaxOrderQty())
        .volumeScale(row.getQuantityPrecision())
        .priceScale(row.getPricePrecision())
        .counterMinimumAmount(row.getMinOrderAmount())
        .marketOrderEnabled("online".equals(row.getStatus()))
        .build();
  }

  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    Objects.requireNonNull(instrument, "instrument must not be null");
    BitgetUtaV3Category category = BitgetUtaV3Adapters.toCategory(instrument);
    String symbol = BitgetUtaV3Adapters.toString(instrument);
    try {
      List<BitgetUtaV3Ticker> tickers = getTickers(category, symbol);
      if (tickers == null || tickers.isEmpty()) {
        throw new ExchangeException("No ticker for " + category.getWireName() + ":" + symbol);
      }
      return BitgetUtaV3Adapters.toTicker(tickers.get(0), instrument);
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  @Override
  public List<Ticker> getTickers(Params params) throws IOException {
    List<Ticker> result = new ArrayList<>();
    try {
      for (BitgetUtaV3Category category :
          new BitgetUtaV3Category[] {
            BitgetUtaV3Category.SPOT,
            BitgetUtaV3Category.USDT_FUTURES,
            BitgetUtaV3Category.COIN_FUTURES,
            BitgetUtaV3Category.USDC_FUTURES
          }) {
        for (BitgetUtaV3Ticker ticker : getTickers(category, null)) {
          Instrument instrument = instrumentForTicker(category, ticker);
          if (instrument != null) {
            result.add(BitgetUtaV3Adapters.toTicker(ticker, instrument));
          }
        }
      }
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
    return result;
  }

  private Instrument instrumentForTicker(BitgetUtaV3Category category, BitgetUtaV3Ticker ticker) {
    try {
      // resolve via instruments list to get category-correct instrument identity
      List<BitgetUtaV3Instrument> rows = getInstruments(category, ticker.getSymbol());
      if (rows != null && !rows.isEmpty()) {
        return BitgetUtaV3Adapters.toInstrument(rows.get(0));
      }
    } catch (IOException ignored) {
      // fall through to symbol-only mapping
    }
    return null;
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return getOrderBook((Instrument) currencyPair, args);
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    Objects.requireNonNull(instrument, "instrument must not be null");
    BitgetUtaV3Category category = BitgetUtaV3Adapters.toCategory(instrument);
    String symbol = BitgetUtaV3Adapters.toString(instrument);
    Integer limit = null;
    if (args != null && args.length > 0 && args[0] instanceof Number) {
      limit = ((Number) args[0]).intValue();
    }
    try {
      BitgetUtaV3OrderBook book = getOrderBook(symbol, category, limit);
      return BitgetUtaV3Adapters.toOrderBook(book, instrument);
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }
}
