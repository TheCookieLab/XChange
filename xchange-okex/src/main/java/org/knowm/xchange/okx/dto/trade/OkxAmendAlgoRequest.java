package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body entry for amending algorithmic orders via {@code /api/v5/trade/amend-algos}.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkxAmendAlgoRequest {

  @JsonProperty("algoId")
  private String algoId;

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("newSz")
  private String amendedAmount;

  @JsonProperty("newPx")
  private String amendedPrice;
}
