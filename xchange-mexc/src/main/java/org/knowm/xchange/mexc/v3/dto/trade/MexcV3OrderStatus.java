package org.knowm.xchange.mexc.v3.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** MEXC Spot v3 order status. */
public enum MexcV3OrderStatus {
  NEW,
  FILLED,
  PARTIALLY_FILLED,
  CANCELED,
  PARTIALLY_CANCELED;

  @JsonValue
  public String wireValue() {
    return name();
  }

  @JsonCreator
  public static MexcV3OrderStatus fromWireValue(String value) {
    return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
  }
}
