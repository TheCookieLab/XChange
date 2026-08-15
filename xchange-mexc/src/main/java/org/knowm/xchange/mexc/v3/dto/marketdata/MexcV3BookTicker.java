package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Order book ticker from {@code GET /api/v3/ticker/bookTicker}. */
public class MexcV3BookTicker {

  private final String symbol;
  private final String bidPrice;
  private final String bidQty;
  private final String askPrice;
  private final String askQty;

  public MexcV3BookTicker(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("bidPrice") String bidPrice,
      @JsonProperty("bidQty") String bidQty,
      @JsonProperty("askPrice") String askPrice,
      @JsonProperty("askQty") String askQty) {
    this.symbol = symbol;
    this.bidPrice = bidPrice;
    this.bidQty = bidQty;
    this.askPrice = askPrice;
    this.askQty = askQty;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getBidPrice() {
    return bidPrice;
  }

  public String getBidQty() {
    return bidQty;
  }

  public String getAskPrice() {
    return askPrice;
  }

  public String getAskQty() {
    return askQty;
  }
}
