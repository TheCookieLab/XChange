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
 * Stop limit good-til-cancelled order configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseStopLimitStopLimitGtc {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal limitPrice;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal stopPrice;
  private final CoinbaseStopPriceDirection stopDirection;

  @JsonCreator
  public CoinbaseStopLimitStopLimitGtc(
      @JsonProperty("base_size") BigDecimal baseSize,
      @JsonProperty("limit_price") BigDecimal limitPrice,
      @JsonProperty("stop_price") BigDecimal stopPrice,
      @JsonProperty("stop_direction") CoinbaseStopPriceDirection stopDirection) {
    this.baseSize = baseSize;
    this.limitPrice = limitPrice;
    this.stopPrice = stopPrice;
    this.stopDirection = stopDirection;
  }

  @Override
  public String toString() {
    return "CoinbaseStopLimitStopLimitGtc [baseSize=" + baseSize + ", limitPrice=" + limitPrice
        + ", stopPrice=" + stopPrice + ", stopDirection=" + stopDirection + "]";
  }
}
