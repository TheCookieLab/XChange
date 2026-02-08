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
  private final String baseCurrencyId;
  private final String quoteCurrencyId;
  private final String productType;
  private final String productVenue;
  private final CoinbaseFutureProductDetails futureProductDetails;

  public CoinbaseProductResponse(@JsonProperty("product_id") String productId,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("price_percentage_change_24h") BigDecimal pricePercentageChange24H,
      @JsonProperty("volume_24h") BigDecimal volume24H,
      @JsonProperty("volume_percentage_change_24h") BigDecimal volumePercentageChange24H,
      @JsonProperty("approximate_quote_24h_volume") BigDecimal approximateQuoteVolume24H) {
    this(
        productId,
        price,
        pricePercentageChange24H,
        volume24H,
        volumePercentageChange24H,
        approximateQuoteVolume24H,
        null,
        null,
        null,
        null,
        null);
  }

  @JsonCreator
  public CoinbaseProductResponse(@JsonProperty("product_id") String productId,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("price_percentage_change_24h") BigDecimal pricePercentageChange24H,
      @JsonProperty("volume_24h") BigDecimal volume24H,
      @JsonProperty("volume_percentage_change_24h") BigDecimal volumePercentageChange24H,
      @JsonProperty("approximate_quote_24h_volume") BigDecimal approximateQuoteVolume24H,
      @JsonProperty("base_currency_id") String baseCurrencyId,
      @JsonProperty("quote_currency_id") String quoteCurrencyId,
      @JsonProperty("product_type") String productType,
      @JsonProperty("product_venue") String productVenue,
      @JsonProperty("future_product_details") CoinbaseFutureProductDetails futureProductDetails) {
    this.productId = productId;
    this.price = price;
    this.pricePercentageChange24H = pricePercentageChange24H;
    this.volume24H = volume24H;
    this.volumePercentageChange24H = volumePercentageChange24H;
    this.approximateQuoteVolume24H = approximateQuoteVolume24H;
    this.baseCurrencyId = baseCurrencyId;
    this.quoteCurrencyId = quoteCurrencyId;
    this.productType = productType;
    this.productVenue = productVenue;
    this.futureProductDetails = futureProductDetails;
  }

  @Override
  public String toString() {
    return "CoinbaseProductResponse [productId=" + productId + ", price="
        + price + ", pricePercentageChange24H=" + pricePercentageChange24H + ", volume24H="
        + volume24H + ", volumePercentageChange24H=" + volumePercentageChange24H
        + ", approximateQuoteVolume24H=" + approximateQuoteVolume24H
        + ", baseCurrencyId=" + baseCurrencyId
        + ", quoteCurrencyId=" + quoteCurrencyId
        + ", productType=" + productType
        + ", productVenue=" + productVenue
        + ", futureProductDetails=" + futureProductDetails + "]";
  }

}
