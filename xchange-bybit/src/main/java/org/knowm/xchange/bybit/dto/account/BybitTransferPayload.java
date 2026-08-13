package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Payload for {@code /v5/asset/transfer/inter-transfer}. */
@Builder
@Jacksonized
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BybitTransferPayload {

  @JsonProperty("transferId")
  String transferId;

  @JsonProperty("coin")
  String coin;

  @JsonProperty("amount")
  String amount;

  @JsonProperty("fromAccountType")
  String fromAccountType;

  @JsonProperty("toAccountType")
  String toAccountType;
}
