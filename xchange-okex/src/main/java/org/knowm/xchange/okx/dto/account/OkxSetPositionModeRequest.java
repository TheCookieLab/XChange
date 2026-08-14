package org.knowm.xchange.okx.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.ToString;

/** https://www.okx.com/docs-v5/en/#rest-api-account-set-position-mode */
@Builder
@ToString
public class OkxSetPositionModeRequest {

  /** long_short_mode: long/short mode, net_mode: net mode. */
  @JsonProperty("posMode")
  private String positionMode;

  /** Account level; only required when the account level is 2 or 3. */
  @JsonProperty("acctLv")
  private String accountLevel;
}
