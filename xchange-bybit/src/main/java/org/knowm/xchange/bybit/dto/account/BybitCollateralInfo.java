package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Collateral asset info from {@code /v5/account/collateral-info}. */
@Builder
@Jacksonized
@Value
public class BybitCollateralInfo {

  @JsonProperty("currency")
  String currency;

  @JsonProperty("hourlyBorrowRate")
  String hourlyBorrowRate;

  @JsonProperty("maxBorrowAmount")
  String maxBorrowAmount;

  @JsonProperty("borrowableAmount")
  String borrowableAmount;

  @JsonProperty("availableToBorrow")
  String availableToBorrow;

  @JsonProperty("borrowAmountUsd")
  String borrowAmountUsd;

  @JsonProperty("collateralRatio")
  String collateralRatio;

  @JsonProperty("collateralRate")
  String collateralRate;

  @JsonProperty("maxCollateralAmount")
  String maxCollateralAmount;

  @JsonProperty("minCollateralAmount")
  String minCollateralAmount;

  @JsonProperty("status")
  String status;
}
