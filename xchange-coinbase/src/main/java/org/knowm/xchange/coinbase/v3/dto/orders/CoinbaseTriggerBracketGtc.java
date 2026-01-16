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
 * Trigger bracket good-til-cancelled configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseTriggerBracketGtc {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal limitPrice;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal stopTriggerPrice;

  @JsonCreator
  public CoinbaseTriggerBracketGtc(
      @JsonProperty("base_size") BigDecimal baseSize,
      @JsonProperty("limit_price") BigDecimal limitPrice,
      @JsonProperty("stop_trigger_price") BigDecimal stopTriggerPrice) {
    this.baseSize = baseSize;
    this.limitPrice = limitPrice;
    this.stopTriggerPrice = stopTriggerPrice;
  }

  @Override
  public String toString() {
    return "CoinbaseTriggerBracketGtc [baseSize=" + baseSize + ", limitPrice=" + limitPrice
        + ", stopTriggerPrice=" + stopTriggerPrice + "]";
  }
}
