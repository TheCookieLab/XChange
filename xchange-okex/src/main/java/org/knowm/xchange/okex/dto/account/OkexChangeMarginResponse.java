package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.knowm.xchange.okx.dto.account.OkxChangeMarginResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxChangeMarginResponse} instead.
 */
@Deprecated
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class OkexChangeMarginResponse {

  private final OkxChangeMarginResponse delegate;

  @JsonCreator
  public OkexChangeMarginResponse(OkxChangeMarginResponse delegate) {
    this.delegate = delegate;
  }

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
