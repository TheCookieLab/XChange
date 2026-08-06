package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated user fills response ({@code GET /portfolio/fills}).
 *
 * @see <a href="https://docs.kalshi.com/api-reference/portfolio/get-fills">Kalshi Get Fills</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiFillsResponse(
    @JsonProperty("fills") List<KalshiFill> fills,
    @JsonProperty("cursor") String cursor) {

  /**
   * Single user fill. Direction is the canonical {@code bookSide} ({@code bid} = buy YES,
   * {@code ask} = sell YES); {@code countFp} is a fixed-point count string and
   * {@code yesPriceDollars} a fixed-point dollar string quoted on the YES leg.
   *
   * @see <a href="https://docs.kalshi.com/getting_started/order_direction">Kalshi order
   *     direction</a>
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiFill(
      @JsonProperty("fill_id") String fillId,
      @JsonProperty("order_id") String orderId,
      @JsonProperty("ticker") String ticker,
      @JsonProperty("book_side") String bookSide,
      @JsonProperty("count_fp") String countFp,
      @JsonProperty("yes_price_dollars") String yesPriceDollars,
      @JsonProperty("created_time") String createdTime) {}
}
