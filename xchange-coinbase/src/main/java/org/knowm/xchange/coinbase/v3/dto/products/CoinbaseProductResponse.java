package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Product response returned by Coinbase Advanced Trade.
 *
 * <p>The increment and minimum fields are exchange-provided decimal quantities. Futures callers must
 * prefer the values in this response and {@link CoinbaseFutureProductDetails}; no contract-size or
 * order-quantity defaults are implied by this transport DTO.
 */
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
  private final BigDecimal baseIncrement;
  private final BigDecimal quoteIncrement;
  private final BigDecimal priceIncrement;
  private final BigDecimal quoteMinSize;
  private final BigDecimal quoteMaxSize;
  private final BigDecimal baseMinSize;
  private final BigDecimal baseMaxSize;
  private final BigDecimal bestBidPrice;
  private final BigDecimal bestAskPrice;
  private final String status;
  private final CoinbaseFutureProductDetails futureProductDetails;

  /**
   * Creates a legacy response containing only the original market summary fields.
   *
   * @deprecated use the full-field constructor so exchange-provided product metadata is retained
   */
  @Deprecated
  public CoinbaseProductResponse(String productId, BigDecimal price,
      BigDecimal pricePercentageChange24H, BigDecimal volume24H,
      BigDecimal volumePercentageChange24H, BigDecimal approximateQuoteVolume24H) {
    this(productId, price, pricePercentageChange24H, volume24H, volumePercentageChange24H,
        approximateQuoteVolume24H, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null);
  }
  /**
   * Creates a response using the historical product identity and venue fields.
   *
   * @deprecated use the full-field constructor so exchange-provided increment and size metadata
   *     is retained
   */
  @Deprecated
  public CoinbaseProductResponse(
      String productId,
      BigDecimal price,
      BigDecimal pricePercentageChange24H,
      BigDecimal volume24H,
      BigDecimal volumePercentageChange24H,
      BigDecimal approximateQuoteVolume24H,
      String baseCurrencyId,
      String quoteCurrencyId,
      String productType,
      String productVenue,
      CoinbaseFutureProductDetails futureProductDetails) {
    this(productId, price, pricePercentageChange24H, volume24H, volumePercentageChange24H,
        approximateQuoteVolume24H, baseCurrencyId, quoteCurrencyId, productType, productVenue,
        null, null, null, null, null, null, null, null, null, null, futureProductDetails);
  }

  @JsonCreator
  public CoinbaseProductResponse(
      @JsonProperty("product_id") String productId,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("price_percentage_change_24h") BigDecimal pricePercentageChange24H,
      @JsonProperty("volume_24h") BigDecimal volume24H,
      @JsonProperty("volume_percentage_change_24h") BigDecimal volumePercentageChange24H,
      @JsonProperty("approximate_quote_24h_volume") BigDecimal approximateQuoteVolume24H,
      @JsonProperty("base_currency_id") String baseCurrencyId,
      @JsonProperty("quote_currency_id") String quoteCurrencyId,
      @JsonProperty("product_type") String productType,
      @JsonProperty("product_venue") String productVenue,
      @JsonProperty("base_increment") BigDecimal baseIncrement,
      @JsonProperty("quote_increment") BigDecimal quoteIncrement,
      @JsonProperty("price_increment") BigDecimal priceIncrement,
      @JsonProperty("quote_min_size") BigDecimal quoteMinSize,
      @JsonProperty("quote_max_size") BigDecimal quoteMaxSize,
      @JsonProperty("base_min_size") BigDecimal baseMinSize,
      @JsonProperty("base_max_size") BigDecimal baseMaxSize,
      @JsonProperty("best_bid_price") BigDecimal bestBidPrice,
      @JsonProperty("best_ask_price") BigDecimal bestAskPrice,
      @JsonProperty("status") String status,
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
    this.baseIncrement = baseIncrement;
    this.quoteIncrement = quoteIncrement;
    this.priceIncrement = priceIncrement;
    this.quoteMinSize = quoteMinSize;
    this.quoteMaxSize = quoteMaxSize;
    this.baseMinSize = baseMinSize;
    this.baseMaxSize = baseMaxSize;
    this.bestBidPrice = bestBidPrice;
    this.bestAskPrice = bestAskPrice;
    this.status = status;
    this.futureProductDetails = futureProductDetails;
  }

  @Override
  public String toString() {
    return "CoinbaseProductResponse [productId=" + productId + ", price=" + price
        + ", baseIncrement=" + baseIncrement + ", baseMinSize=" + baseMinSize
        + ", productType=" + productType + ", productVenue=" + productVenue
        + ", futureProductDetails=" + futureProductDetails + "]";
  }
}
