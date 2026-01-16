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
 * Good-til-date limit order configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseLimitLimitGtd {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal quoteSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal limitPrice;
  private final String endTime;
  private final Boolean postOnly;

  @JsonCreator
  public CoinbaseLimitLimitGtd(
      @JsonProperty("quote_size") BigDecimal quoteSize,
      @JsonProperty("base_size") BigDecimal baseSize,
      @JsonProperty("limit_price") BigDecimal limitPrice,
      @JsonProperty("end_time") String endTime,
      @JsonProperty("post_only") Boolean postOnly) {
    this.quoteSize = quoteSize;
    this.baseSize = baseSize;
    this.limitPrice = limitPrice;
    this.endTime = endTime;
    this.postOnly = postOnly;
  }

  @Override
  public String toString() {
    return "CoinbaseLimitLimitGtd [quoteSize=" + quoteSize + ", baseSize=" + baseSize
        + ", limitPrice=" + limitPrice + ", endTime=" + endTime
        + ", postOnly=" + postOnly + "]";
  }
}
