package org.knowm.xchange.coinbase.v3.dto.pricebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePriceBookEntry {

  private final BigDecimal price;
  private final BigDecimal size;

  @JsonCreator
  public CoinbasePriceBookEntry(@JsonProperty("price") BigDecimal price,
      @JsonProperty("size") BigDecimal size) {
    this.price = price;
    this.size = size;
  }

  @Override
  public String toString() {
    return "CoinbasePriceBookEntry [price=" + price + ", size=" + size + "]";
  }
}
