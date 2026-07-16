package org.knowm.xchange.coinbasederivatives.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Provider ticker, index, mark, funding, and best-book snapshot. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesTicker(
    @JsonProperty("instrument_name") String instrumentName,
    long timestamp,
    @JsonProperty("last_price") BigDecimal lastPrice,
    @JsonProperty("best_bid_price") BigDecimal bestBidPrice,
    @JsonProperty("best_bid_amount") BigDecimal bestBidAmount,
    @JsonProperty("best_ask_price") BigDecimal bestAskPrice,
    @JsonProperty("best_ask_amount") BigDecimal bestAskAmount,
    @JsonProperty("mark_price") BigDecimal markPrice,
    @JsonProperty("index_price") BigDecimal indexPrice,
    @JsonProperty("current_funding") BigDecimal currentFunding,
    @JsonProperty("funding_8h") BigDecimal fundingEightHours,
    @JsonProperty("open_interest") BigDecimal openInterest,
    Stats stats) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Stats(
      BigDecimal high,
      BigDecimal low,
      BigDecimal volume,
      @JsonProperty("price_change") BigDecimal priceChange) {}
}
