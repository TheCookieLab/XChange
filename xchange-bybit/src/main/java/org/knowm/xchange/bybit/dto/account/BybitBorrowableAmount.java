package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Borrowable amount from {@code /v5/account/borrowable-amount}. */
@Builder
@Jacksonized
@Value
public class BybitBorrowableAmount {

  @JsonProperty("currency")
  String currency;

  @JsonProperty("maxBorrowAmount")
  String maxBorrowAmount;

  @JsonProperty("borrowableAmount")
  String borrowableAmount;
}
