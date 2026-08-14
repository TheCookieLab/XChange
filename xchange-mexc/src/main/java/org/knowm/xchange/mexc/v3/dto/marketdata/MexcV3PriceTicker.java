package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Symbol price ticker from {@code GET /api/v3/ticker/price}. */
public class MexcV3PriceTicker {

  private final String symbol;
  private final String price;

  public MexcV3PriceTicker(@JsonProperty("symbol") String symbol, @JsonProperty("price") String price) {
    this.symbol = symbol;
    this.price = price;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getPrice() {
    return price;
  }
}
