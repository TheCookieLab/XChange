package org.knowm.xchange.okx.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.ToString;

/** https://www.okx.com/docs-v5/en/#rest-api-funding-funds-transfer */
@Builder
@ToString
public class OkxTransferRequest {

  @JsonProperty("ccy")
  private String currency;

  @JsonProperty("amt")
  private String amount;

  /** 6: funding account, 18: trading account. */
  @JsonProperty("from")
  private String fromAccount;

  /** 6: funding account, 18: trading account. */
  @JsonProperty("to")
  private String toAccount;

  /** 0: transfer within account, 1: master to sub-account, 2: sub-account to master. */
  @JsonProperty("type")
  private String type;

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("toInstId")
  private String toInstrumentId;
}
