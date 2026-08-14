package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Current average price from {@code GET /api/v3/avgPrice}. */
public class MexcV3AvgPrice {

  /** Number of minutes over which the average is computed. */
  private final int mins;
  private final String price;

  public MexcV3AvgPrice(@JsonProperty("mins") int mins, @JsonProperty("price") String price) {
    this.mins = mins;
    this.price = price;
  }

  public int getMins() {
    return mins;
  }

  public String getPrice() {
    return price;
  }
}
