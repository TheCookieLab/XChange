package org.knowm.xchange.coinbase.v3.dto.portfolios;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response containing a single portfolio or portfolio breakdown.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getportfoliobreakdown">Get Portfolio Breakdown</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePortfolioResponse {

  private final CoinbasePortfolioBreakdown breakdown;
  private final CoinbasePortfolio portfolio;

  @JsonCreator
  public CoinbasePortfolioResponse(
      @JsonProperty("breakdown") CoinbasePortfolioBreakdown breakdown,
      @JsonProperty("portfolio") CoinbasePortfolio portfolio) {
    this.breakdown = breakdown;
    this.portfolio = portfolio;
  }

  @Override
  public String toString() {
    return "CoinbasePortfolioResponse [portfolio=" + (portfolio == null ? null : portfolio.getUuid()) + "]";
  }
}

