package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response from opting in or out of multi-asset collateral for INTX perpetuals.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_optinoroutmultiassetcollateral">Opt In/Out Multi Asset Collateral</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseMultiAssetCollateralResponse {

  private final Boolean success;
  private final String portfolioUuid;

  @JsonCreator
  public CoinbaseMultiAssetCollateralResponse(
      @JsonProperty("success") Boolean success,
      @JsonProperty("portfolio_uuid") String portfolioUuid) {
    this.success = success;
    this.portfolioUuid = portfolioUuid;
  }

  @Override
  public String toString() {
    return "CoinbaseMultiAssetCollateralResponse [success=" + success + ", portfolioUuid=" + portfolioUuid + "]";
  }
}

