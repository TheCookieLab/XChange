package org.knowm.xchange.coinbase.v3.dto.portfolios;

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
 * Represents a monetary amount used in portfolio requests.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbasePortfolioAmount {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal value;
  private final String currency;

  @JsonCreator
  public CoinbasePortfolioAmount(
      @JsonProperty("value") BigDecimal value,
      @JsonProperty("currency") String currency) {
    this.value = value;
    this.currency = currency;
  }

  @Override
  public String toString() {
    return "CoinbasePortfolioAmount [value=" + value + ", currency=" + currency + "]";
  }
}
