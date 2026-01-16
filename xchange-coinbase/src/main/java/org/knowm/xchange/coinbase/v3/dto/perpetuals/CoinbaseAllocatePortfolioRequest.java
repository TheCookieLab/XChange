package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Request payload for allocating collateral to an isolated perpetuals position.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/perpetuals/allocate-portfolio.md">Allocate Portfolio</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseAllocatePortfolioRequest {

  private final String portfolioUuid;
  private final String symbol;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal amount;
  private final String currency;

  @JsonCreator
  public CoinbaseAllocatePortfolioRequest(
      @JsonProperty("portfolio_uuid") String portfolioUuid,
      @JsonProperty("symbol") String symbol,
      @JsonProperty("amount") BigDecimal amount,
      @JsonProperty("currency") String currency) {
    this.portfolioUuid = portfolioUuid;
    this.symbol = symbol;
    this.amount = amount;
    this.currency = currency;
  }

  @Override
  public String toString() {
    return "CoinbaseAllocatePortfolioRequest [portfolioUuid=" + portfolioUuid
        + ", symbol=" + symbol + ", amount=" + amount + ", currency=" + currency + "]";
  }
}
