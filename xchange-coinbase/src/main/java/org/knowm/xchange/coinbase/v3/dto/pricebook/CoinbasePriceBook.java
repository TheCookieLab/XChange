package org.knowm.xchange.coinbase.v3.dto.pricebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePriceBook {

  private final List<CoinbasePriceBookEntry> bids;
  private final List<CoinbasePriceBookEntry> asks;
  private final String productId;
  private final String time;

  @JsonCreator
  public CoinbasePriceBook(@JsonProperty("product_id") String productId,
      @JsonProperty("bids") List<CoinbasePriceBookEntry> bids,
      @JsonProperty("asks") List<CoinbasePriceBookEntry> asks, @JsonProperty("time") String time) {
    this.productId = productId;
    this.time = time;
    this.bids = bids;
    this.asks = asks;
  }

  @Override
  public String toString() {
    return "CoinbasePriceBook [productId=" + productId + ", time=" + time + ", bids=" + bids
        + ", asks=" + asks + "]";
  }
}
