package org.knowm.xchange.mexc.v3.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** MEXC Spot v3 order side. */
public enum MexcV3OrderSide {
  BUY,
  SELL;

  @JsonValue
  public String wireValue() {
    return name();
  }

  @JsonCreator
  public static MexcV3OrderSide fromWireValue(String value) {
    return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
  }
}
