package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Leverage modification result; {@code leverage} is echoed as configured.
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/modify-leverage-uta">Modify Futures
 *     Leverage (UTA)</a>
 */
@Data
public class UtaModifyLeverageResult {

  @JsonProperty("code")
  private String code;

  @JsonProperty("leverage")
  private String leverage;
}
