package org.knowm.xchange.bitso.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Order type enum */
public enum BitsoOrderType {
  MARKET("market"),
  LIMIT("limit");

  private final String value;

  BitsoOrderType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static BitsoOrderType fromValue(String value) {
    for (BitsoOrderType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown order type: " + value);
  }
}
