package org.knowm.xchange.binance.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MarginType {
  ISOLATED,
  CROSSED;

  @JsonCreator
  public static MarginType getOrderSide(String s) {
    if (s == null) {
      throw new IllegalArgumentException("Unknown margin type null.");
    }
    try {
      return MarginType.valueOf(s);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown margin type " + s + ".", e);
    }
  }
}
