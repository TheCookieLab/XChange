package org.knowm.xchange.bitso.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Time in force enum for orders */
public enum BitsoTimeInForce {
  GOOD_TILL_CANCELLED("goodtillcancelled"),
  FILL_OR_KILL("fillorkill"),
  IMMEDIATE_OR_CANCEL("immediateorcancel"),
  POST_ONLY("postonly");

  private final String value;

  BitsoTimeInForce(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static BitsoTimeInForce fromValue(String value) {
    for (BitsoTimeInForce tif : values()) {
      if (tif.value.equals(value)) {
        return tif;
      }
    }
    throw new IllegalArgumentException("Unknown time in force: " + value);
  }
}
