package org.knowm.xchange.binance.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OrderSide {
  BUY,
  SELL;

  @JsonCreator
  public static OrderSide getOrderSide(String s) {
    if (s == null) {
      throw new IllegalArgumentException("Unknown order side null.");
    }
    try {
      return OrderSide.valueOf(s);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown order side " + s + ".", e);
    }
  }
}
