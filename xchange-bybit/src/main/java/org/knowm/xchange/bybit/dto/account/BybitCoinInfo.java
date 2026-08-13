package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One coin entry from {@code /v5/asset/coin/query-info}. */
@Builder
@Jacksonized
@Value
public class BybitCoinInfo {

  @JsonProperty("name")
  String name;

  @JsonProperty("coin")
  String coin;

  @JsonProperty("remainAmount")
  String remainAmount;

  @JsonProperty("chains")
  java.util.List<BybitCoinChain> chains;

  @Builder
  @Jacksonized
  @Value
  public static class BybitCoinChain {

    @JsonProperty("chain")
    String chain;

    @JsonProperty("chainType")
    String chainType;

    @JsonProperty("withdrawFee")
    String withdrawFee;

    @JsonProperty("depositMin")
    String depositMin;

    @JsonProperty("withdrawMin")
    String withdrawMin;

    @JsonProperty("chainDeposit")
    String chainDeposit;

    @JsonProperty("chainWithdraw")
    String chainWithdraw;

    @JsonProperty("minAccuracy")
    String minAccuracy;

    @JsonProperty("withdrawPercentageFee")
    String withdrawPercentageFee;

    @JsonProperty("safeConfirmNumber")
    String safeConfirmNumber;

    @JsonProperty("withdrawMax")
    String withdrawMax;

    @JsonProperty("confirmation")
    String confirmation;

    @JsonProperty("contractAddress")
    String contractAddress;
  }
}
