package org.knowm.xchange.bitget.uta.v3.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * UTA account configuration.
 *
 * <p>{@code GET /api/v3/account/info} returns the account mode and permission profile. Account mode
 * values seen in docs: {@code basic}, {@code advanced}, {@code isolated}.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3AccountInfo {

  @JsonProperty("mode")
  private String mode;

  @JsonProperty("uid")
  private String uid;

  @JsonProperty("tradePermission")
  private String tradePermission;

  @JsonProperty("assetPermission")
  private String assetPermission;

  @JsonProperty("isDayTradeEnabled")
  private String isDayTradeEnabled;

  @JsonProperty("takerFeeRate")
  private String takerFeeRate;

  @JsonProperty("makerFeeRate")
  private String makerFeeRate;
}
