package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.beans.ConstructorProperties;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseMarketTrade {

  private final String tradeId;
  private final String productId;
  private final BigDecimal price;
  private final BigDecimal size;
  private final String time;
  private final String side;
  private final BigDecimal bid;
  private final BigDecimal ask;
  private final String exchange;

  @JsonCreator
  @ConstructorProperties({"trade_id", "product_id", "price", "size", "time", "side", "bid", "ask",
      "exchange"})
  public CoinbaseMarketTrade(@JsonProperty("trade_id") String tradeId,
      @JsonProperty("product_id") String productId, @JsonProperty("price") BigDecimal price,
      @JsonProperty("size") BigDecimal size, @JsonProperty("time") String time,
      @JsonProperty("side") String side, @JsonProperty("bid") BigDecimal bid,
      @JsonProperty("ask") BigDecimal ask, @JsonProperty("exchange") String exchange) {
    this.tradeId = tradeId;
    this.productId = productId;
    this.price = price;
    this.size = size;
    this.time = time;
    this.side = side;
    this.bid = bid;
    this.ask = ask;
    this.exchange = exchange;
  }

  @Override
  public String toString() {
    return "CoinbaseMarketTrade [tradeId=" + tradeId + ", productId=" + productId + ", price="
        + price + ", size=" + size + ", time=" + time + ", bid=" + bid + ", ask=" + ask + "]";
  }

}
