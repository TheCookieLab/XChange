package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Kalshi order book response. Levels are {@code [priceCents, count]} pairs: the {@code yes} list
 * holds YES bids, the {@code no} list holds NO bids (equivalently YES asks at the complement
 * price).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiOrderBookResponse(
    @JsonProperty("orderbook") KalshiOrderBookLevels orderbook) {

  /** YES and NO bid level lists. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiOrderBookLevels(
      @JsonProperty("yes") List<List<Integer>> yes,
      @JsonProperty("no") List<List<Integer>> no) {}
}
