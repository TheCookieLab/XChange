package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body entry for cancelling algorithmic orders via {@code /api/v5/trade/cancel-algos}. Up
 * to 10 entries may be submitted in one request.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkxCancelAlgoRequest {

  @JsonProperty("algoId")
  private String algoId;

  @JsonProperty("instId")
  private String instrumentId;
}
