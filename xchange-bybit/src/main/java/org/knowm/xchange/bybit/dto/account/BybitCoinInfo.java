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

    @JsonProperty("withdrawalFee")
    String withdrawalFee;

    @JsonProperty("minDeposit")
    String minDeposit;

    @JsonProperty("minWithdrawal")
    String minWithdrawal;

    @JsonProperty("depositStatus")
    String depositStatus;

    @JsonProperty("withdrawalStatus")
    String withdrawalStatus;

    @JsonProperty("confirmation")
    String confirmation;

    @JsonProperty("contractAddress")
    String contractAddress;
  }
}
