package org.knowm.xchange.kucoin.uta;

import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;

/** Trade-type resolution from XChange instruments. */
public final class UtaTradeTypes {

  private UtaTradeTypes() {}

  /** @return {@code "FUTURES"} for derivative instruments, {@code "SPOT"} otherwise */
  public static String of(Instrument instrument) {
    return instrument instanceof FuturesContract ? "FUTURES" : "SPOT";
  }
}
