package org.knowm.xchange.okx;

import java.util.HashMap;
import java.util.Map;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;

/** Supplies deterministic instrument codes without loading exchange metadata over HTTP. */
public final class OkxInstrumentCodeFixture implements AutoCloseable {

  private final Map<Instrument, Long> original =
      OkxAdapters.snapshotInstrumentToInstrumentIdMapForTesting();

  public OkxInstrumentCodeFixture() {
    Map<Instrument, Long> fixture = new HashMap<>(original);
    fixture.put(CurrencyPair.BTC_USDT, 42L);
    OkxAdapters.replaceInstrumentToInstrumentIdMapForTesting(fixture);
  }

  @Override
  public void close() {
    OkxAdapters.replaceInstrumentToInstrumentIdMapForTesting(original);
  }
}
