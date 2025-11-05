package org.knowm.xchange.coinbase.v3.dto.portfolios;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Portfolio breakdown details including balances and other portfolio information.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePortfolioBreakdown {

  private final String portfolio;
  private final String portfolioBalances;
  private final BigDecimal spotPositionNotional;
  private final BigDecimal perpetualsPositionNotional;
  private final BigDecimal futuresPositionNotional;
  private final BigDecimal unrealizedPnl;

  @JsonCreator
  public CoinbasePortfolioBreakdown(
      @JsonProperty("portfolio") String portfolio,
      @JsonProperty("portfolio_balances") String portfolioBalances,
      @JsonProperty("spot_position_notional") BigDecimal spotPositionNotional,
      @JsonProperty("perpetuals_position_notional") BigDecimal perpetualsPositionNotional,
      @JsonProperty("futures_position_notional") BigDecimal futuresPositionNotional,
      @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl) {
    this.portfolio = portfolio;
    this.portfolioBalances = portfolioBalances;
    this.spotPositionNotional = spotPositionNotional;
    this.perpetualsPositionNotional = perpetualsPositionNotional;
    this.futuresPositionNotional = futuresPositionNotional;
    this.unrealizedPnl = unrealizedPnl;
  }

  @Override
  public String toString() {
    return "CoinbasePortfolioBreakdown [portfolio=" + portfolio + "]";
  }
}

