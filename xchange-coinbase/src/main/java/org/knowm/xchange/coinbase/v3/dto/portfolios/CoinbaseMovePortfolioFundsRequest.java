package org.knowm.xchange.coinbase.v3.dto.portfolios;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Request payload for moving funds between portfolios.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/portfolios/move-portfolios-funds.md">Move Portfolio Funds</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseMovePortfolioFundsRequest {

  private final CoinbasePortfolioAmount funds;
  private final String sourcePortfolioUuid;
  private final String targetPortfolioUuid;

  @JsonCreator
  public CoinbaseMovePortfolioFundsRequest(
      @JsonProperty("funds") CoinbasePortfolioAmount funds,
      @JsonProperty("source_portfolio_uuid") String sourcePortfolioUuid,
      @JsonProperty("target_portfolio_uuid") String targetPortfolioUuid) {
    this.funds = funds;
    this.sourcePortfolioUuid = sourcePortfolioUuid;
    this.targetPortfolioUuid = targetPortfolioUuid;
  }

  @Override
  public String toString() {
    return "CoinbaseMovePortfolioFundsRequest [funds=" + funds
        + ", sourcePortfolioUuid=" + sourcePortfolioUuid
        + ", targetPortfolioUuid=" + targetPortfolioUuid + "]";
  }
}
