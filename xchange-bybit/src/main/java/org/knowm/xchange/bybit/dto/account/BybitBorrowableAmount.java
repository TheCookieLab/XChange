package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Max borrowable amount from {@code GET /v5/spot-margin-trade/max-borrowable}. */
@Builder
@Jacksonized
@Value
public class BybitBorrowableAmount {

  @JsonProperty("currency")
  String currency;

  @JsonProperty("maxLoan")
  String maxLoan;
}
