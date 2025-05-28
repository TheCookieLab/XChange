package org.knowm.xchange.coinbase.v3.dto.pricebook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseProductPriceBookResponse {

  private final CoinbasePriceBook priceBook;
  private final BigDecimal spreadAbsolute;
  private final BigDecimal midMarket;
  private final BigDecimal spreadBps;
  private final BigDecimal last;

  public CoinbaseProductPriceBookResponse(@JsonProperty("pricebook") CoinbasePriceBook priceBook,
      @JsonProperty("last") BigDecimal last, @JsonProperty("mid_market") BigDecimal midMarket,
      @JsonProperty("spread_bps") BigDecimal spreadBps,
      @JsonProperty("spread_absolute") BigDecimal spreadAbsolute) {
    this.spreadAbsolute = spreadAbsolute;
    this.priceBook = priceBook;
    this.midMarket = midMarket;
    this.spreadBps = spreadBps;
    this.last = last;
  }

  @Override
  public String toString() {
    return "CoinbaseProductPriceBookResponse{" + "priceBook=" + priceBook + ", last=" + last
        + ", midMarket=" + midMarket + "}";
  }
}
