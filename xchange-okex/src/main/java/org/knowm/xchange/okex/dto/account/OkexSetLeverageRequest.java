package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxSetLeverageRequest} instead.
 */
@Deprecated
@Builder
@Getter
@ToString
public class OkexSetLeverageRequest {

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("ccy")
  private String currency;

  @JsonProperty("lever")
  private String leverage;

  @JsonProperty("mgnMode")
  private String marginMode;

  @JsonProperty("posSide")
  private String positionSide;

  public OkxSetLeverageRequest to() {
    return OkxSetLeverageRequest.builder()
        .instrumentId(instrumentId)
        .currency(currency)
        .leverage(leverage)
        .marginMode(marginMode)
        .positionSide(positionSide)
        .build();
  }
}
