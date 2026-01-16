package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStreamingTrade {

  private final String productId;
  private final String tradeId;
  private final BigDecimal price;
  private final BigDecimal size;
  private final String side;
  private final String time;

  @JsonCreator
  public CoinbaseStreamingTrade(
      @JsonProperty("product_id") String productId,
      @JsonProperty("trade_id") String tradeId,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("size") BigDecimal size,
      @JsonProperty("side") String side,
      @JsonProperty("time") String time) {
    this.productId = productId;
    this.tradeId = tradeId;
    this.price = price;
    this.size = size;
    this.side = side;
    this.time = time;
  }

  public String getProductId() {
    return productId;
  }

  public String getTradeId() {
    return tradeId;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public BigDecimal getSize() {
    return size;
  }

  public String getSide() {
    return side;
  }

  public String getTime() {
    return time;
  }
}
