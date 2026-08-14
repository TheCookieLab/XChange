package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.knowm.xchange.okx.dto.trade.OkxAmendAlgoRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxAmendAlgoRequest} instead.
 */
@Deprecated
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkexAmendAlgoRequest {

  @JsonProperty("algoId")
  private String algoId;

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("newSz")
  private String amendedAmount;

  @JsonProperty("newPx")
  private String amendedPrice;

  public OkxAmendAlgoRequest to() {
    return OkxAmendAlgoRequest.builder()
        .algoId(algoId)
        .instrumentId(instrumentId)
        .amendedAmount(amendedAmount)
        .amendedPrice(amendedPrice)
        .build();
  }
}
