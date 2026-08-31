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
   * <p>Absent optional values (including blank strings) remain {@code null}; only a nonblank
   * unrecognized exchange value is represented by {@link #UNKNOWN_MARGIN_TYPE}.
   *
   * @param value Coinbase margin-type value, which may be absent
   * @return the matching margin type, {@code null} for null/blank input, or
   *     {@link #UNKNOWN_MARGIN_TYPE} for a nonblank unrecognized value
   */
  @JsonCreator
  public static CoinbaseMarginType fromValue(String value) {
    if (value == null || value.trim().isEmpty()) {
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
