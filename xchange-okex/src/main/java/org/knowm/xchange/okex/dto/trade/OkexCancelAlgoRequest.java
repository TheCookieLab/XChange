package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.knowm.xchange.okx.dto.trade.OkxCancelAlgoRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxCancelAlgoRequest} instead.
 */
@Deprecated
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkexCancelAlgoRequest {

  @JsonProperty("algoId")
  private String algoId;

  @JsonProperty("instId")
  private String instrumentId;

  public OkxCancelAlgoRequest to() {
    return OkxCancelAlgoRequest.builder()
        .algoId(algoId)
        .instrumentId(instrumentId)
        .build();
  }
}
