package org.knowm.xchange.coinbase.v3.dto.permissions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Response containing API key permissions.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getapikeyspermissions">Get API Key Permissions</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseKeyPermissionsResponse {

  private final Boolean canView;
  private final Boolean canTrade;
  private final Boolean canTransfer;
  private final String portfolioUuid;
  private final List<String> portfolioTypes;
  private final String portfolioType;

  @JsonCreator
  public CoinbaseKeyPermissionsResponse(
      @JsonProperty("can_view") Boolean canView,
      @JsonProperty("can_trade") Boolean canTrade,
      @JsonProperty("can_transfer") Boolean canTransfer,
      @JsonProperty("portfolio_uuid") String portfolioUuid,
      @JsonProperty("portfolio_types") List<String> portfolioTypes,
      @JsonProperty("portfolio_type") String portfolioType) {
    this.canView = canView;
    this.canTrade = canTrade;
    this.canTransfer = canTransfer;
    this.portfolioUuid = portfolioUuid;
    this.portfolioTypes = portfolioTypes == null ? Collections.emptyList() : Collections.unmodifiableList(portfolioTypes);
    this.portfolioType = portfolioType;
  }

  @Override
  public String toString() {
    return "CoinbaseKeyPermissionsResponse [canView=" + canView + ", canTrade=" + canTrade + ", canTransfer=" + canTransfer + ", portfolioUuid=" + portfolioUuid + "]";
  }
}

