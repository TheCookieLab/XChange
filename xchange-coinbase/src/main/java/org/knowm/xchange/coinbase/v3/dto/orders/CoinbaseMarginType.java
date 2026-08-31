package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Margin types supported for Advanced Trade derivative orders. */
public enum CoinbaseMarginType {
  CROSS,
  ISOLATED,
  UNKNOWN_MARGIN_TYPE;

  /**
   * Maps current and future Coinbase wire values without rejecting the containing order.
   *
   * @param value Coinbase margin-type value
   * @return the matching margin type, or {@link #UNKNOWN_MARGIN_TYPE} for an unrecognized value
   */
  @JsonCreator
  public static CoinbaseMarginType fromValue(String value) {
    if (value == null) {
      return null;
    }
    switch (value) {
      case "CROSS":
        return CROSS;
      case "ISOLATED":
        return ISOLATED;
      default:
        return UNKNOWN_MARGIN_TYPE;
    }
  }
}
