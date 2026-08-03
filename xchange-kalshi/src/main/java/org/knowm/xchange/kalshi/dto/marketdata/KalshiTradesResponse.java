package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paginated public trades response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiTradesResponse(
    @JsonProperty("trades") List<KalshiTradeRecord> trades,
    @JsonProperty("cursor") String cursor) {

  /**
   * Single public trade. {@code takerSide} is {@code yes} or {@code no}; {@code yesPrice} is
   * integer cents.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiTradeRecord(
      @JsonProperty("trade_id") String tradeId,
      @JsonProperty("ticker") String ticker,
      @JsonProperty("count") Integer count,
      @JsonProperty("yes_price") Integer yesPrice,
      @JsonProperty("taker_side") String takerSide,
      @JsonProperty("created_time") String createdTime) {}
}
