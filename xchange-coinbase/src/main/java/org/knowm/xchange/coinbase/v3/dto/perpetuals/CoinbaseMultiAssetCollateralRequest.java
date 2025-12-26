package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Request payload for opting in or out of multi-asset collateral.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/perpetuals/opt-in-or-out.md">Opt In or Out of Multi Asset Collateral</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseMultiAssetCollateralRequest {

  private final String portfolioUuid;
  private final Boolean multiAssetCollateralEnabled;

  @JsonCreator
  public CoinbaseMultiAssetCollateralRequest(
      @JsonProperty("portfolio_uuid") String portfolioUuid,
      @JsonProperty("multi_asset_collateral_enabled") Boolean multiAssetCollateralEnabled) {
    this.portfolioUuid = portfolioUuid;
    this.multiAssetCollateralEnabled = multiAssetCollateralEnabled;
  }

  @Override
  public String toString() {
    return "CoinbaseMultiAssetCollateralRequest [portfolioUuid=" + portfolioUuid
        + ", multiAssetCollateralEnabled=" + multiAssetCollateralEnabled + "]";
  }
}
