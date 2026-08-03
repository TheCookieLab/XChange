package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * V2 event-order create response (HTTP 201). Count and price fields are fixed-point strings on
 * this surface.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiCreateOrderResponse(
    @JsonProperty("order_id") String orderId,
    @JsonProperty("client_order_id") String clientOrderId,
    @JsonProperty("fill_count") String fillCount,
    @JsonProperty("remaining_count") String remainingCount,
    @JsonProperty("average_fill_price") String averageFillPrice,
    @JsonProperty("ts_ms") Long tsMs) {}
