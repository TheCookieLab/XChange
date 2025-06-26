package org.knowm.xchange.bitso.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Order status enum */
public enum BitsoOrderStatus {
  QUEUED("queued"),
  OPEN("open"),
  PARTIALLY_FILLED("partially filled");

  private final String value;

  BitsoOrderStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static BitsoOrderStatus fromValue(String value) {
    for (BitsoOrderStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown order status: " + value);
  }
}
