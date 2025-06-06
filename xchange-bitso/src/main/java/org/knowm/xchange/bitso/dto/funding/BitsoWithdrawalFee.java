package org.knowm.xchange.bitso.dto.funding;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/** Bitso Withdrawal Fee DTO */
@Value
@Builder
@Jacksonized
public class BitsoWithdrawalFee {

  /** Fee amount */
  private final BigDecimal amount;

  /** Fee type (fixed, percentage) */
  private final String type;
}
