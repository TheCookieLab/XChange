package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * CLOB user fill from {@code GET /data/trades}. {@code traderSide} distinguishes whether the
 * authenticated user's order was the {@code TAKER} or the {@code MAKER} of the fill: for a taker
 * fill the user's order id is {@code takerOrderId}, for a maker fill the user's legs are the
 * {@link #makerOrders()} entries whose {@code maker_address} matches the account.
 *
 * <p>{@code size}, {@code matchedAmount} and {@code makerAmount}/{@code takerAmount} strings are
 * 6-decimal fixed-point micro-units (100000000 = 100 shares); {@code price} is decimal dollars per
 * share (see https://docs.polymarket.com/api-reference/trade/get-trades).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketUserTrade(
    @JsonProperty("id") String id,
    @JsonProperty("taker_order_id") String takerOrderId,
    @JsonProperty("market") String market,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("outcome") String outcome,
    @JsonProperty("side") String side,
    @JsonProperty("size") String size,
    @JsonProperty("price") String price,
    @JsonProperty("status") String status,
    @JsonProperty("match_time") String matchTime,
    @JsonProperty("trader_side") String traderSide,
    @JsonProperty("owner") String owner,
    @JsonProperty("maker_address") String makerAddress,
    @JsonProperty("maker_orders") List<MakerOrder> makerOrders) {

  /**
   * One maker leg of a {@link PolymarketUserTrade}; {@code matchedAmount} is 6-decimal fixed-point
   * micro-units and {@code price} decimal dollars per share.
   *
   * @param orderId maker order id
   * @param owner owner UUID of the maker order
   * @param makerAddress ethereum address of the maker order owner
   * @param matchedAmount shares matched on this maker order (fixed-point micro-units)
   * @param price dollars per share of the maker order
   * @param side maker side: {@code BUY} or {@code SELL} on the outcome token
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MakerOrder(
      @JsonProperty("order_id") String orderId,
      @JsonProperty("owner") String owner,
      @JsonProperty("maker_address") String makerAddress,
      @JsonProperty("matched_amount") String matchedAmount,
      @JsonProperty("price") String price,
      @JsonProperty("side") String side) {}
}
