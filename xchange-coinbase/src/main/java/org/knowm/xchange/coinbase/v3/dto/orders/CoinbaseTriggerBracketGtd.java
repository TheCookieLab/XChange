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
 * Trigger bracket good-til-date configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseTriggerBracketGtd {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal limitPrice;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal stopTriggerPrice;
  private final String endTime;

  @JsonCreator
  public CoinbaseTriggerBracketGtd(
      @JsonProperty("base_size") BigDecimal baseSize,
      @JsonProperty("limit_price") BigDecimal limitPrice,
      @JsonProperty("stop_trigger_price") BigDecimal stopTriggerPrice,
      @JsonProperty("end_time") String endTime) {
    this.baseSize = baseSize;
    this.limitPrice = limitPrice;
    this.stopTriggerPrice = stopTriggerPrice;
    this.endTime = endTime;
  }

  @Override
  public String toString() {
    return "CoinbaseTriggerBracketGtd [baseSize=" + baseSize + ", limitPrice=" + limitPrice
        + ", stopTriggerPrice=" + stopTriggerPrice + ", endTime=" + endTime + "]";
  }
}
