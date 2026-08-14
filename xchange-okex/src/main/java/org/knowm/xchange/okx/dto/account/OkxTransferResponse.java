package org.knowm.xchange.okx.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** https://www.okx.com/docs-v5/en/#rest-api-funding-funds-transfer */
@Getter
@NoArgsConstructor
@ToString
public class OkxTransferResponse {

  @JsonProperty("transId")
  private String transferId;
}
