package org.knowm.xchange.mexc.v3.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * MEXC Spot v3 order type.
 *
 * <p>Wire values: {@code LIMIT}, {@code MARKET}, {@code LIMIT_MAKER}, {@code IMMEDIATE_OR_CANCEL}
 * (IOC), {@code FILL_OR_KILL} (FOK). MEXC expresses time-in-force semantics through the order type
 * rather than a separate parameter.
 */
public enum MexcV3OrderType {
  LIMIT,
  MARKET,
  LIMIT_MAKER,
  IMMEDIATE_OR_CANCEL,
  FILL_OR_KILL,
  /** Present in provider order history payloads only (query-only value). */
  STOP_MARKET_ORDER;

  @JsonValue
  public String wireValue() {
    return name();
  }

  @JsonCreator
  public static MexcV3OrderType fromWireValue(String value) {
    return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
  }
}
