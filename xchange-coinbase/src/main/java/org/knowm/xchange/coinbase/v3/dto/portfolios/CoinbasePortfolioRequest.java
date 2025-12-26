package org.knowm.xchange.coinbase.v3.dto.portfolios;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Request payload for creating or editing a portfolio.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/portfolios/create-portfolio.md">Create Portfolio</a>
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/portfolios/edit-portfolio.md">Edit Portfolio</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbasePortfolioRequest {

  private final String name;

  @JsonCreator
  public CoinbasePortfolioRequest(@JsonProperty("name") String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "CoinbasePortfolioRequest [name=" + name + "]";
  }
}
