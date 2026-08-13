package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Collateral asset info from {@code /v5/account/collateral-info}, bound to the documented wire keys. */
@Builder
@Jacksonized
@Value
public class BybitCollateralInfo {

  @JsonProperty("currency")
  String currency;

  @JsonProperty("hourlyBorrowRate")
  String hourlyBorrowRate;

  /** Max borrow amount, shared across main-sub UIDs. */
  @JsonProperty("maxBorrowingAmount")
  String maxBorrowingAmount;

  /** Maximum limit for interest-free borrowing. */
  @JsonProperty("freeBorrowingLimit")
  String freeBorrowingLimit;

  /** Borrowing amount exempt from interest charges. */
  @JsonProperty("freeBorrowAmount")
  String freeBorrowAmount;

  @JsonProperty("borrowAmount")
  String borrowAmount;

  /** Sum of borrowing amounts of other accounts under the same main account. */
  @JsonProperty("otherBorrowAmount")
  String otherBorrowAmount;

  /** Available amount to borrow, shared across main-sub UIDs. */
  @JsonProperty("availableToBorrow")
  String availableToBorrow;

  @JsonProperty("borrowable")
  boolean borrowable;

  /** Borrow usage rate: sum of main & sub accounts borrowAmount / maxBorrowingAmount. */
  @JsonProperty("borrowUsageRate")
  String borrowUsageRate;

  /** Whether the currency can be used as margin collateral. */
  @JsonProperty("marginCollateral")
  boolean marginCollateral;

  /** Whether the user has enabled collateral for this currency. */
  @JsonProperty("collateralSwitch")
  boolean collateralSwitch;

  /** Deprecated: inaccurate since the Tiered Collateral value logic (2025-02-19). */
  @JsonProperty("collateralRatio")
  String collateralRatio;

  /** Deprecated: always empty, refer to {@code freeBorrowingLimit}. */
  @JsonProperty("freeBorrowingAmount")
  String freeBorrowingAmount;
}
