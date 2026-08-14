package org.knowm.xchange.mexc.v3.dto.marketdata;

/** MEXC Spot v3 kline interval, as sent on the wire for {@code GET /api/v3/klines}. */
public enum MexcV3KlineInterval {
  /** 1 minute. */
  M1("1m"),
  /** 5 minutes. */
  M5("5m"),
  /** 15 minutes. */
  M15("15m"),
  /** 30 minutes. */
  M30("30m"),
  /** 60 minutes. */
  M60("60m"),
  /** 4 hours. */
  H4("4h"),
  /** 1 day. */
  D1("1d"),
  /** 1 month. */
  MONTH1("1M");

  private final String wireValue;

  MexcV3KlineInterval(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The interval as transmitted to the REST API. */
  public String getWireValue() {
    return wireValue;
  }

  /** The interval as used in WebSocket channel names (e.g. {@code Min1}, {@code Hour4}). */
  public String getStreamValue() {
    switch (this) {
      case M1:
        return "Min1";
      case M5:
        return "Min5";
      case M15:
        return "Min15";
      case M30:
        return "Min30";
      case M60:
        return "Min60";
      case H4:
        return "Hour4";
      case D1:
        return "Day1";
      case MONTH1:
        return "Month1";
      default:
        throw new IllegalStateException("Unmapped interval " + this);
    }
  }

  /** Maps a REST wire interval ({@code 1m}, {@code 4h}, ...) to the enum. */
  public static MexcV3KlineInterval fromWireValue(String wireValue) {
    for (MexcV3KlineInterval interval : values()) {
      if (interval.wireValue.equals(wireValue)) {
        return interval;
      }
    }
    throw new IllegalArgumentException("Unknown MEXC kline interval: " + wireValue);
  }
}
