package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStreamingTicker {

  private final String productId;
  private final BigDecimal price;
  private final BigDecimal volume24H;
  private final BigDecimal high24H;
  private final BigDecimal low24H;
  private final BigDecimal open24H;
  private final BigDecimal bestBid;
  private final BigDecimal bestAsk;
  private final String time;

  @JsonCreator
  public CoinbaseStreamingTicker(
      @JsonProperty("product_id") String productId,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("volume_24_h") BigDecimal volume24H,
      @JsonProperty("high_24_h") BigDecimal high24H,
      @JsonProperty("low_24_h") BigDecimal low24H,
      @JsonProperty("open_24_h") BigDecimal open24H,
      @JsonProperty("best_bid") BigDecimal bestBid,
      @JsonProperty("best_ask") BigDecimal bestAsk,
      @JsonProperty("time") String time) {
    this.productId = productId;
    this.price = price;
    this.volume24H = volume24H;
    this.high24H = high24H;
    this.low24H = low24H;
    this.open24H = open24H;
    this.bestBid = bestBid;
    this.bestAsk = bestAsk;
    this.time = time;
  }

  public String getProductId() {
    return productId;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public BigDecimal getVolume24H() {
    return volume24H;
  }

  public BigDecimal getHigh24H() {
    return high24H;
  }

  public BigDecimal getLow24H() {
    return low24H;
  }

  public BigDecimal getOpen24H() {
    return open24H;
  }

  public BigDecimal getBestBid() {
    return bestBid;
  }

  public BigDecimal getBestAsk() {
    return bestAsk;
  }

  public String getTime() {
    return time;
  }
}
