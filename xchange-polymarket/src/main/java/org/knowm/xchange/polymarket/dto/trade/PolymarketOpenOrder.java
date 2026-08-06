package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CLOB order record from the {@code /data/order(s)} read surface. {@code side} is {@code BUY} or
 * {@code SELL} on the token carried in {@code assetId}; {@code originalSize}/{@code sizeMatched}
 * are 6-decimal fixed-point micro-units (100000000 = 100 shares) and {@code price} decimal dollars
 * per share (see https://docs.polymarket.com/api-reference/trade/get-user-orders). {@code status}
 * carries the current CLOB V2 names ({@code ORDER_STATUS_LIVE}, {@code ORDER_STATUS_MATCHED}, ...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketOpenOrder(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("owner") String owner,
    @JsonProperty("maker_address") String makerAddress,
    @JsonProperty("market") String market,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("outcome") String outcome,
    @JsonProperty("side") String side,
    @JsonProperty("original_size") String originalSize,
    @JsonProperty("size_matched") String sizeMatched,
    @JsonProperty("price") String price,
    @JsonProperty("expiration") String expiration,
    @JsonProperty("order_type") String orderType,
    @JsonProperty("created_at") String createdAt) {}
