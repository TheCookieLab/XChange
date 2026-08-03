package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paginated user fills response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiFillsResponse(
    @JsonProperty("fills") List<KalshiFill> fills,
    @JsonProperty("cursor") String cursor) {

  /**
   * Single user fill. Prices are integer cents; {@code action} is {@code buy}/{@code sell} and
   * {@code side} is {@code yes}/{@code no}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiFill(
      @JsonProperty("fill_id") String fillId,
      @JsonProperty("order_id") String orderId,
      @JsonProperty("ticker") String ticker,
      @JsonProperty("action") String action,
      @JsonProperty("side") String side,
      @JsonProperty("count") Integer count,
      @JsonProperty("yes_price") Integer yesPrice,
      @JsonProperty("no_price") Integer noPrice,
      @JsonProperty("created_time") String createdTime) {}
}
