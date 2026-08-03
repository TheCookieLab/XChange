package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * V2 event-order create request body. {@code price} is a fixed-point dollar string (up to 6
 * decimal places), {@code count} a fixed-point string with up to 2 decimal places, and {@code
 * side} is {@code bid} (buy YES) or {@code ask} (sell YES).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KalshiOrderRequest(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("client_order_id") String clientOrderId,
    @JsonProperty("side") String side,
    @JsonProperty("price") String price,
    @JsonProperty("count") String count,
    @JsonProperty("time_in_force") String timeInForce,
    @JsonProperty("post_only") Boolean postOnly,
    @JsonProperty("cancel_order_on_pause") Boolean cancelOrderOnPause,
    @JsonProperty("reduce_only") Boolean reduceOnly,
    @JsonProperty("self_trade_prevention_type") String selfTradePreventionType) {}
