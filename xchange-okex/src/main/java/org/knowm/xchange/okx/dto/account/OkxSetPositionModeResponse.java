package org.knowm.xchange.okx.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** https://www.okx.com/docs-v5/en/#rest-api-account-set-position-mode */
@Getter
@NoArgsConstructor
@ToString
public class OkxSetPositionModeResponse {

  @JsonProperty("posMode")
  private String positionMode;

  @JsonProperty("acctLv")
  private String accountLevel;
}
