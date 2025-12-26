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
 * Smart Order Routing limit IOC configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseSorLimitIoc {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal quoteSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal limitPrice;

  @JsonCreator
  public CoinbaseSorLimitIoc(
      @JsonProperty("quote_size") BigDecimal quoteSize,
      @JsonProperty("base_size") BigDecimal baseSize,
      @JsonProperty("limit_price") BigDecimal limitPrice) {
    this.quoteSize = quoteSize;
    this.baseSize = baseSize;
    this.limitPrice = limitPrice;
  }

  @Override
  public String toString() {
    return "CoinbaseSorLimitIoc [quoteSize=" + quoteSize + ", baseSize=" + baseSize
        + ", limitPrice=" + limitPrice + "]";
  }
}
