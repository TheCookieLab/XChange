package org.knowm.xchange.okx.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/** https://www.okx.com/docs-v5/en/#rest-api-account-increase-decrease-margin * */
@Builder
public class OkxChangeMarginResponse {
  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("type")
  private String type;

  @JsonProperty("amt")
  private String amount;

  @JsonProperty("leverage")
  private String leverage;

  @JsonProperty("ccy")
  private String currency;
}
