package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseProductResponse {

  private final String productId;
  private final BigDecimal price;
  private final BigDecimal pricePercentageChange24H;
  private final BigDecimal volume24H;
  private final BigDecimal volumePercentageChange24H;
  private final BigDecimal approximateQuoteVolume24H;

  @JsonCreator
  public CoinbaseProductResponse(@JsonProperty("product_id") String productId,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("price_percentage_change_24h") BigDecimal pricePercentageChange24H,
      @JsonProperty("volume_24h") BigDecimal volume24H,
      @JsonProperty("volume_percentage_change_24h") BigDecimal volumePercentageChange24H,
      @JsonProperty("approximate_quote_24h_volume") BigDecimal approximateQuoteVolume24H) {
    this.productId = productId;
    this.price = price;
    this.pricePercentageChange24H = pricePercentageChange24H;
    this.volume24H = volume24H;
    this.volumePercentageChange24H = volumePercentageChange24H;
    this.approximateQuoteVolume24H = approximateQuoteVolume24H;
  }

  @Override
  public String toString() {
    return "CoinbaseProductResponse [productId=" + productId + ", price="
        + price + ", pricePercentageChange24H=" + pricePercentageChange24H + ", volume24H="
        + volume24H + ", volumePercentageChange24H=" + volumePercentageChange24H
        + ", approximateQuoteVolume24H=" + approximateQuoteVolume24H + "]";
  }

}
