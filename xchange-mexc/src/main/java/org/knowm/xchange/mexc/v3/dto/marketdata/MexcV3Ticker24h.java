package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 24-hour rolling ticker from {@code GET /api/v3/ticker/24hr}. */
public class MexcV3Ticker24h {

  private final String symbol;
  private final String priceChange;
  private final String priceChangePercent;
  private final String prevClosePrice;
  private final String lastPrice;
  private final String bidPrice;
  private final String bidQty;
  private final String askPrice;
  private final String askQty;
  private final String openPrice;
  private final String highPrice;
  private final String lowPrice;
  private final String volume;
  private final String quoteVolume;
  private final long openTime;
  private final long closeTime;
  private final Long count;

  public MexcV3Ticker24h(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("priceChange") String priceChange,
      @JsonProperty("priceChangePercent") String priceChangePercent,
      @JsonProperty("prevClosePrice") String prevClosePrice,
      @JsonProperty("lastPrice") String lastPrice,
      @JsonProperty("bidPrice") String bidPrice,
      @JsonProperty("bidQty") String bidQty,
      @JsonProperty("askPrice") String askPrice,
      @JsonProperty("askQty") String askQty,
      @JsonProperty("openPrice") String openPrice,
      @JsonProperty("highPrice") String highPrice,
      @JsonProperty("lowPrice") String lowPrice,
      @JsonProperty("volume") String volume,
      @JsonProperty("quoteVolume") String quoteVolume,
      @JsonProperty("openTime") long openTime,
      @JsonProperty("closeTime") long closeTime,
      @JsonProperty("count") Long count) {
    this.symbol = symbol;
    this.priceChange = priceChange;
    this.priceChangePercent = priceChangePercent;
    this.prevClosePrice = prevClosePrice;
    this.lastPrice = lastPrice;
    this.bidPrice = bidPrice;
    this.bidQty = bidQty;
    this.askPrice = askPrice;
    this.askQty = askQty;
    this.openPrice = openPrice;
    this.highPrice = highPrice;
    this.lowPrice = lowPrice;
    this.volume = volume;
    this.quoteVolume = quoteVolume;
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.count = count;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getPriceChange() {
    return priceChange;
  }

  public String getPriceChangePercent() {
    return priceChangePercent;
  }

  public String getPrevClosePrice() {
    return prevClosePrice;
  }

  public String getLastPrice() {
    return lastPrice;
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

  public String getOpenPrice() {
    return openPrice;
  }

  public String getHighPrice() {
    return highPrice;
  }

  public String getLowPrice() {
    return lowPrice;
  }

  public String getVolume() {
    return volume;
  }

  /** Quote-asset volume; may be {@code null} per provider responses. */
  public String getQuoteVolume() {
    return quoteVolume;
  }

  public long getOpenTime() {
    return openTime;
  }

  public long getCloseTime() {
    return closeTime;
  }

  /** Number of trades in the window; may be {@code null} per provider responses. */
  public Long getCount() {
    return count;
  }
}
