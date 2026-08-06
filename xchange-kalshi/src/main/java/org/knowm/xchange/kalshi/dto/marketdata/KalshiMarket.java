package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Kalshi market record. Prices ({@code yesBidDollars}, {@code yesAskDollars},
 * {@code lastPriceDollars}) are fixed-point dollar strings with up to 4 decimal places;
 * sizes and volume ({@code volumeFp}, {@code openInterestFp}) are fixed-point count strings
 * with 2 decimals. {@code priceRanges} is the source of truth for the market's valid price
 * grid: any price on a band's {@code step} is valid, any off-grid price is rejected.
 *
 * @see <a href="https://docs.kalshi.com/api-reference/market/get-market">Kalshi Get Market</a>
 * @see <a href="https://docs.kalshi.com/getting_started/fixed_point_migration">Kalshi
 *     Fixed-Point Representation</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiMarket(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("event_ticker") String eventTicker,
    @JsonProperty("title") String title,
    @JsonProperty("status") String status,
    @JsonProperty("yes_bid_dollars") String yesBidDollars,
    @JsonProperty("yes_ask_dollars") String yesAskDollars,
    @JsonProperty("last_price_dollars") String lastPriceDollars,
    @JsonProperty("volume_fp") String volumeFp,
    @JsonProperty("open_interest_fp") String openInterestFp,
    @JsonProperty("notional_value_dollars") String notionalValueDollars,
    @JsonProperty("price_ranges") List<KalshiPriceRange> priceRanges) {

  /**
   * Valid price band on a market's grid; {@code step} is the tick size in dollars for prices
   * inside the band.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiPriceRange(
      @JsonProperty("start") String start,
      @JsonProperty("end") String end,
      @JsonProperty("step") String step) {}
}
