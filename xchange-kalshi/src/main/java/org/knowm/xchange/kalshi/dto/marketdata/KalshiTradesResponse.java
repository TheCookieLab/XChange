package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated public trades response ({@code GET /markets/trades}).
 *
 * @see <a href="https://docs.kalshi.com/api-reference/market/get-trades">Kalshi Get Trades</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiTradesResponse(
    @JsonProperty("trades") List<KalshiTradeRecord> trades,
    @JsonProperty("cursor") String cursor) {

  /**
   * Single public trade. Direction is the canonical {@code takerBookSide} ({@code bid} = buy
   * YES, {@code ask} = sell YES; see <a href="https://docs.kalshi.com/getting_started/order_direction">Kalshi
   * order direction</a>); {@code countFp} is a fixed-point count string and
   * {@code yesPriceDollars} a fixed-point dollar string quoted on the YES leg.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiTradeRecord(
      @JsonProperty("trade_id") String tradeId,
      @JsonProperty("ticker") String ticker,
      @JsonProperty("count_fp") String countFp,
      @JsonProperty("yes_price_dollars") String yesPriceDollars,
      @JsonProperty("taker_book_side") String takerBookSide,
      @JsonProperty("created_time") String createdTime) {}
}
