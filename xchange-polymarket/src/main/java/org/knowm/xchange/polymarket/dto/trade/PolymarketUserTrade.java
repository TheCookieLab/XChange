package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** CLOB user fill. {@code traderSide} distinguishes MAKER/TAKER involvement. */
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
    @JsonProperty("owner") String owner) {}
