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
 * Time-weighted average price limit order configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseTwapLimitGtd {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal quoteSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;
  private final String startTime;
  private final String endTime;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal limitPrice;
  private final String numberBuckets;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal bucketSize;
  private final String bucketDuration;

  @JsonCreator
  public CoinbaseTwapLimitGtd(
      @JsonProperty("quote_size") BigDecimal quoteSize,
      @JsonProperty("base_size") BigDecimal baseSize,
      @JsonProperty("start_time") String startTime,
      @JsonProperty("end_time") String endTime,
      @JsonProperty("limit_price") BigDecimal limitPrice,
      @JsonProperty("number_buckets") String numberBuckets,
      @JsonProperty("bucket_size") BigDecimal bucketSize,
      @JsonProperty("bucket_duration") String bucketDuration) {
    this.quoteSize = quoteSize;
    this.baseSize = baseSize;
    this.startTime = startTime;
    this.endTime = endTime;
    this.limitPrice = limitPrice;
    this.numberBuckets = numberBuckets;
    this.bucketSize = bucketSize;
    this.bucketDuration = bucketDuration;
  }

  @Override
  public String toString() {
    return "CoinbaseTwapLimitGtd [quoteSize=" + quoteSize + ", baseSize=" + baseSize
        + ", startTime=" + startTime + ", endTime=" + endTime + ", limitPrice=" + limitPrice
        + ", numberBuckets=" + numberBuckets + ", bucketSize=" + bucketSize
        + ", bucketDuration=" + bucketDuration + "]";
  }
}
