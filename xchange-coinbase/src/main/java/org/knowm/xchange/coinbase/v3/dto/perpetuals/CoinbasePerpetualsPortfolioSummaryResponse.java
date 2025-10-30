package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Response containing perpetuals portfolio summary for INTX perpetuals trading.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getintxportfoliosummary">Get Perpetuals Portfolio Summary</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePerpetualsPortfolioSummaryResponse {

  private final CoinbasePerpetualsPortfolioSummary portfolios;
  private final CoinbasePerpetualsPortfolioSummary summary;

  @JsonCreator
  public CoinbasePerpetualsPortfolioSummaryResponse(
      @JsonProperty("portfolios") CoinbasePerpetualsPortfolioSummary portfolios,
      @JsonProperty("summary") CoinbasePerpetualsPortfolioSummary summary) {
    this.portfolios = portfolios;
    this.summary = summary;
  }

  @Override
  public String toString() {
    return "CoinbasePerpetualsPortfolioSummaryResponse [portfolios=" + portfolios + "]";
  }

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CoinbasePerpetualsPortfolioSummary {
    private final String portfolioUuid;
    private final String collateralCurrency;
    private final BigDecimal totalBalance;
    private final BigDecimal availableBalance;
    private final BigDecimal unrealizedPnl;
    private final BigDecimal buyingPower;
    private final BigDecimal totalPositionNotional;
    private final List<CoinbasePerpetualsPosition> positions;

    @JsonCreator
    public CoinbasePerpetualsPortfolioSummary(
        @JsonProperty("portfolio_uuid") String portfolioUuid,
        @JsonProperty("collateral_currency") String collateralCurrency,
        @JsonProperty("total_balance") BigDecimal totalBalance,
        @JsonProperty("available_balance") BigDecimal availableBalance,
        @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
        @JsonProperty("buying_power") BigDecimal buyingPower,
        @JsonProperty("total_position_notional") BigDecimal totalPositionNotional,
        @JsonProperty("positions") List<CoinbasePerpetualsPosition> positions) {
      this.portfolioUuid = portfolioUuid;
      this.collateralCurrency = collateralCurrency;
      this.totalBalance = totalBalance;
      this.availableBalance = availableBalance;
      this.unrealizedPnl = unrealizedPnl;
      this.buyingPower = buyingPower;
      this.totalPositionNotional = totalPositionNotional;
      this.positions = positions == null ? Collections.emptyList() : Collections.unmodifiableList(positions);
    }
  }
}

