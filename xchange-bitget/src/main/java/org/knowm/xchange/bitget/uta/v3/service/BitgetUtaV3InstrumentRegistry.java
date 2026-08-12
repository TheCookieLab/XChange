package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3Adapters;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Instrument;
import org.knowm.xchange.instrument.Instrument;

/**
 * Lazily resolves provider {@code (category, symbol)} pairs to XChange {@link Instrument}s by
 * consulting the instruments endpoint once per category and caching the result.
 *
 * <p>Used when converting order/fill/position DTOs, whose identity is provider-native, back to
 * XChange instruments. The cache is per-service-instance and is not tied to {@code remoteInit()}.
 * Instrument-catalog failures propagate so a transient public-endpoint failure can never silently
 * pair private order/fill/position data with a {@code null} instrument.
 */
class BitgetUtaV3InstrumentRegistry {

  private final BitgetUtaV3MarketDataServiceRaw marketData;
  private final Map<BitgetUtaV3Category, Map<String, Instrument>> byCategory =
      new ConcurrentHashMap<>();

  BitgetUtaV3InstrumentRegistry(BitgetUtaV3MarketDataServiceRaw marketData) {
    this.marketData = marketData;
  }

  /** Resolves an instrument, or {@code null} when the category/symbol is unknown. */
  Instrument resolve(BitgetUtaV3Category category, String symbol) throws IOException {
    if (symbol == null) {
      return null;
    }
    Map<String, Instrument> mapping = byCategory.get(category);
    if (mapping == null) {
      mapping = loadCategory(category);
      byCategory.put(category, mapping);
    }
    Instrument instrument = mapping.get(symbol);
    if (instrument == null) {
      // symbol not in cache (e.g. delisted): try a fresh fetch once
      mapping = loadCategory(category);
      byCategory.put(category, mapping);
      instrument = mapping.get(symbol);
    }
    return instrument;
  }

  private Map<String, Instrument> loadCategory(BitgetUtaV3Category category) throws IOException {
    Map<String, Instrument> mapping = new ConcurrentHashMap<>();
    List<BitgetUtaV3Instrument> rows = marketData.getInstruments(category, null);
    if (rows != null) {
      for (BitgetUtaV3Instrument row : rows) {
        mapping.put(row.getSymbol(), BitgetUtaV3Adapters.toInstrument(row));
      }
    }
    return mapping;
  }
}
