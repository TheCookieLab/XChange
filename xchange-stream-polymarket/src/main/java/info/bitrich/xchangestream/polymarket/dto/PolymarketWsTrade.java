package info.bitrich.xchangestream.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Trade event ({@code event_type=trade}) on the authenticated Polymarket user channel. When {@code
 * trader_side} is {@code TAKER} the taker fields describe the user's fill; when it is {@code
 * MAKER} each {@link MakerOrder} entry describes one of the user's resting orders that matched.
 *
 * @param id trade id
 * @param takerOrderId taker order id (the user's order when {@code trader_side=TAKER})
 * @param market condition id
 * @param assetId outcome-token id traded
 * @param side taker side: {@code BUY} or {@code SELL} on the outcome token
 * @param size shares traded
 * @param feeRateBps fee rate in basis points
 * @param price dollars per share
 * @param status settlement status ({@code MATCHED}/{@code MINED}/{@code CONFIRMED}/...)
 * @param matchTime match time in unix seconds (string-encoded)
 * @param traderSide {@code TAKER} or {@code MAKER} relative to the subscribed user
 * @param timestamp event time in unix milliseconds (string-encoded)
 * @param makerOrders matched maker legs (populated when {@code trader_side=MAKER})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketWsTrade(
    @JsonProperty("id") String id,
    @JsonProperty("taker_order_id") String takerOrderId,
    @JsonProperty("market") String market,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("side") String side,
    @JsonProperty("size") String size,
    @JsonProperty("fee_rate_bps") String feeRateBps,
    @JsonProperty("price") String price,
    @JsonProperty("status") String status,
    @JsonProperty("match_time") String matchTime,
    @JsonProperty("trader_side") String traderSide,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("maker_orders") List<MakerOrder> makerOrders) {

  /**
   * One matched maker leg of a {@link PolymarketWsTrade}.
   *
   * @param orderId maker order id
   * @param matchedAmount shares matched on this maker order
   * @param price dollars per share of the maker order
   * @param side maker side: {@code BUY} or {@code SELL} on the outcome token
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MakerOrder(
      @JsonProperty("order_id") String orderId,
      @JsonProperty("matched_amount") String matchedAmount,
      @JsonProperty("price") String price,
      @JsonProperty("side") String side) {}
}
