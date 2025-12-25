package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStreamingCandle {

  private final String productId;
  private final String start;
  private final BigDecimal open;
  private final BigDecimal close;
  private final BigDecimal high;
  private final BigDecimal low;
  private final BigDecimal volume;

  @JsonCreator
  public CoinbaseStreamingCandle(
      @JsonProperty("product_id") String productId,
      @JsonProperty("start") String start,
      @JsonProperty("open") BigDecimal open,
      @JsonProperty("close") BigDecimal close,
      @JsonProperty("high") BigDecimal high,
      @JsonProperty("low") BigDecimal low,
      @JsonProperty("volume") BigDecimal volume) {
    this.productId = productId;
    this.start = start;
    this.open = open;
    this.close = close;
    this.high = high;
    this.low = low;
    this.volume = volume;
  }

  public String getProductId() {
    return productId;
  }

  public String getStart() {
    return start;
  }

  public BigDecimal getOpen() {
    return open;
  }

  public BigDecimal getClose() {
    return close;
  }

  public BigDecimal getHigh() {
    return high;
  }

  public BigDecimal getLow() {
    return low;
  }

  public BigDecimal getVolume() {
    return volume;
  }
}
