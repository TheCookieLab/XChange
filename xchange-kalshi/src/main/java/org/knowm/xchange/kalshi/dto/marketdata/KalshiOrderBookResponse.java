package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Kalshi order book response ({@code GET /markets/{ticker}/orderbook}).
 *
 * <p>The provider returns only bids: {@code yes_dollars} holds YES bid levels and
 * {@code no_dollars} holds NO bid levels (equivalently YES asks at the complement price
 * {@code 1 - price}). Each level is a fixed-point string pair
 * {@code [price_dollars, count_fp]} — for example {@code ["0.4200", "13.00"]} — where the
 * price has up to 4 decimal places and the count up to 2.
 *
 * @see <a href="https://docs.kalshi.com/api-reference/market/get-market-orderbook">Kalshi
 *     Get Market Orderbook</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiOrderBookResponse(
    @JsonProperty("orderbook_fp") KalshiOrderBookLevels orderbookFp) {

  /** YES and NO bid level lists as {@code [dollars, fp]} string pairs. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiOrderBookLevels(
      @JsonProperty("yes_dollars") List<List<String>> yesDollars,
      @JsonProperty("no_dollars") List<List<String>> noDollars) {}
}
