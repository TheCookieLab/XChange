package org.knowm.xchange.bitso.dto.funding;

import com.fasterxml.jackson.annotation.JsonValue;

/** Funding transaction status enum */
public enum BitsoFundingStatus {
  PENDING("pending"),
  COMPLETE("complete"),
  CANCELLED("cancelled"),
  FAILED("failed");

  private final String value;

  BitsoFundingStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static BitsoFundingStatus fromValue(String value) {
    for (BitsoFundingStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown funding status: " + value);
  }
}
