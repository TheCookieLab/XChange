package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Fill-or-kill market order configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseMarketMarketFok {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal quoteSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;

  @JsonCreator
  public CoinbaseMarketMarketFok(
      @JsonProperty("quote_size") BigDecimal quoteSize,
      @JsonProperty("base_size") BigDecimal baseSize) {
    this.quoteSize = quoteSize;
    this.baseSize = baseSize;
  }

  @Override
  public String toString() {
    return "CoinbaseMarketMarketFok [quoteSize=" + quoteSize + ", baseSize=" + baseSize + "]";
  }
}
