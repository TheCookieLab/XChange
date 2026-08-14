package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.knowm.xchange.okx.dto.account.OkxChangeMarginRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxChangeMarginRequest} instead.
 */
@Deprecated
@Builder
@Getter
public class OkexChangeMarginRequest {

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("type")
  private String type;

  @JsonProperty("amt")
  private String amount;

  @JsonProperty("ccy")
  private String currency;

  @JsonProperty("auto")
  private boolean auto;

  @JsonProperty("loanTrans")
  private boolean loanTrans;

  public OkxChangeMarginRequest to() {
    return OkxChangeMarginRequest.builder()
        .instrumentId(instrumentId)
        .posSide(posSide)
        .type(type)
        .amount(amount)
        .currency(currency)
        .auto(auto)
        .loanTrans(loanTrans)
        .build();
  }
}
