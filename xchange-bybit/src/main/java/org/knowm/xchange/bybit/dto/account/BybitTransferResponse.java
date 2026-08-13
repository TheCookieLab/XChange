package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Response of {@code /v5/asset/transfer/inter-transfer}. */
@Builder
@Jacksonized
@Value
public class BybitTransferResponse {

  @JsonProperty("transferId")
  String transferId;

  @JsonProperty("status")
  String status;
}
