package org.knowm.xchange.bitso.dto.funding;

import com.fasterxml.jackson.annotation.JsonValue;

/** Withdrawal fee type enum */
public enum BitsoWithdrawalFeeType {
  FIXED("fixed"),
  PERCENTAGE("percentage");

  private final String value;

  BitsoWithdrawalFeeType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static BitsoWithdrawalFeeType fromValue(String value) {
    for (BitsoWithdrawalFeeType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown fee type: " + value);
  }
}
