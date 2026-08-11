package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Futures leverage modification request.
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/modify-leverage-uta">Modify Futures
 *     Leverage (UTA)</a>
 */
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class UtaModifyLeverageRequest {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("leverage")
  private String leverage;
}
