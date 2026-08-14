package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One public recent trade from {@code GET /api/v3/trades}. */
public class MexcV3Trade {

  private final long id;
  private final String price;
  private final String qty;
  private final String quoteQty;
  private final long time;
  private final boolean isBuyerMaker;
  private final boolean isBestMatch;

  public MexcV3Trade(
      @JsonProperty("id") long id,
      @JsonProperty("price") String price,
      @JsonProperty("qty") String qty,
      @JsonProperty("quoteQty") String quoteQty,
      @JsonProperty("time") long time,
      @JsonProperty("isBuyerMaker") boolean isBuyerMaker,
      @JsonProperty("isBestMatch") boolean isBestMatch) {
    this.id = id;
    this.price = price;
    this.qty = qty;
    this.quoteQty = quoteQty;
    this.time = time;
    this.isBuyerMaker = isBuyerMaker;
    this.isBestMatch = isBestMatch;
  }

  public long getId() {
    return id;
  }

  public String getPrice() {
    return price;
  }

  public String getQty() {
    return qty;
  }

  public String getQuoteQty() {
    return quoteQty;
  }

  /** Trade time in Unix milliseconds. */
  public long getTime() {
    return time;
  }

  public boolean isBuyerMaker() {
    return isBuyerMaker;
  }

  public boolean isBestMatch() {
    return isBestMatch;
  }
}
