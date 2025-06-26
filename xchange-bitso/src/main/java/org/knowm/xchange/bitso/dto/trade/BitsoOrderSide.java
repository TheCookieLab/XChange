package org.knowm.xchange.bitso.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Order side enum */
public enum BitsoOrderSide {
  BUY("buy"),
  SELL("sell");

  private final String value;

  BitsoOrderSide(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static BitsoOrderSide fromValue(String value) {
    for (BitsoOrderSide side : values()) {
      if (side.value.equals(value)) {
        return side;
      }
    }
    throw new IllegalArgumentException("Unknown order side: " + value);
  }
}
