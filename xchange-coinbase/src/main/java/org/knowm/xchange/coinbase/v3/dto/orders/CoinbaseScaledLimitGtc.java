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
import java.util.List;
import lombok.Getter;

/**
 * Scaled limit order configuration.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseScaledLimitGtc {

  private final List<CoinbaseLimitLimitGtc> orders;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal quoteSize;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal baseSize;
  private final Integer numOrders;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal minPrice;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal maxPrice;
  private final CoinbaseScaledPriceDistribution priceDistribution;
  private final CoinbaseScaledSizeDistribution sizeDistribution;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal sizeDiff;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal sizeRatio;

  @JsonCreator
  public CoinbaseScaledLimitGtc(
      @JsonProperty("orders") List<CoinbaseLimitLimitGtc> orders,
      @JsonProperty("quote_size") BigDecimal quoteSize,
      @JsonProperty("base_size") BigDecimal baseSize,
      @JsonProperty("num_orders") Integer numOrders,
      @JsonProperty("min_price") BigDecimal minPrice,
      @JsonProperty("max_price") BigDecimal maxPrice,
      @JsonProperty("price_distribution") CoinbaseScaledPriceDistribution priceDistribution,
      @JsonProperty("size_distribution") CoinbaseScaledSizeDistribution sizeDistribution,
      @JsonProperty("size_diff") BigDecimal sizeDiff,
      @JsonProperty("size_ratio") BigDecimal sizeRatio) {
    this.orders = orders;
    this.quoteSize = quoteSize;
    this.baseSize = baseSize;
    this.numOrders = numOrders;
    this.minPrice = minPrice;
    this.maxPrice = maxPrice;
    this.priceDistribution = priceDistribution;
    this.sizeDistribution = sizeDistribution;
    this.sizeDiff = sizeDiff;
    this.sizeRatio = sizeRatio;
  }

  @Override
  public String toString() {
    return "CoinbaseScaledLimitGtc [orders=" + orders + ", quoteSize=" + quoteSize
        + ", baseSize=" + baseSize + ", numOrders=" + numOrders
        + ", minPrice=" + minPrice + ", maxPrice=" + maxPrice
        + ", priceDistribution=" + priceDistribution
        + ", sizeDistribution=" + sizeDistribution
        + ", sizeDiff=" + sizeDiff + ", sizeRatio=" + sizeRatio + "]";
  }
}
