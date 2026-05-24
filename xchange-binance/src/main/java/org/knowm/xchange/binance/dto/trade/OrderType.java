package org.knowm.xchange.binance.dto.trade;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OrderType {
  LIMIT,
  MARKET,
  TAKE_PROFIT_LIMIT,
  STOP_MARKET,
  STOP_LOSS_LIMIT,
  STOP_LOSS,
  STOP,
  TAKE_PROFIT,
  TAKE_PROFIT_MARKET,
  TRAILING_STOP_MARKET,
  LIMIT_MAKER;

  @JsonCreator
  public static OrderType getOrderType(String s) {
    if (s == null) {
      throw new IllegalArgumentException("Unknown order type null.");
    }
    try {
      return OrderType.valueOf(s);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown order type " + s + ".", e);
    }
  }
}
