package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Coinbase Advanced Trade (v3) margin rates for futures products.
 *
 * <p>Coinbase reports long and short rates separately. The interpretation (for example whether
 * these represent borrowing costs or margin requirements) is product- and venue-dependent; this
 * class is a thin DTO that preserves the values as provided by the API.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseMarginRate {

  private final BigDecimal longMarginRate;
  private final BigDecimal shortMarginRate;

  @JsonCreator
  public CoinbaseMarginRate(
      @JsonProperty("long_margin_rate") String longMarginRate,
      @JsonProperty("short_margin_rate") String shortMarginRate) {
    this.longMarginRate = CoinbaseFutureProductDetails.parseBigDecimal(longMarginRate);
    this.shortMarginRate = CoinbaseFutureProductDetails.parseBigDecimal(shortMarginRate);
  }

  @Override
  public String toString() {
    return "CoinbaseMarginRate [longMarginRate=" + longMarginRate
        + ", shortMarginRate=" + shortMarginRate + "]";
  }
}

