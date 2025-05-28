package org.knowm.xchange.coinbase.v3.dto.pricebook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseBestBidAsksResponse {

  @Getter
  private final List<CoinbasePriceBook> priceBooks;

  public CoinbaseBestBidAsksResponse(
      @JsonProperty("pricebooks") List<CoinbasePriceBook> priceBooks) {
    this.priceBooks = priceBooks;
  }

  @Override
  public String toString() {
    return "CoinbaseBestBidAsksResponse [priceBooks=" + priceBooks + "]";
  }
}
