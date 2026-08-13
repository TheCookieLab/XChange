package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One borrow record from {@code /v5/account/borrow-history}. */
@Builder
@Jacksonized
@Value
public class BybitBorrowHistory {

  @JsonProperty("currency")
  String currency;

  @JsonProperty("createdTime")
  String createdTime;

  @JsonProperty("borrowCost")
  String borrowCost;

  @JsonProperty("borrowAmount")
  String borrowAmount;

  @JsonProperty("unrealisedLoss")
  String unrealisedLoss;

  @JsonProperty("repaidAmount")
  String repaidAmount;

  @JsonProperty("interestAmount")
  String interestAmount;

  @JsonProperty("borrowOrderId")
  String borrowOrderId;

  @JsonProperty("borrowType")
  String borrowType;
}
