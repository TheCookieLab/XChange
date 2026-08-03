package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Legacy order record as returned by the {@code /portfolio/orders} read surface. Prices are
 * integer cents; {@code action} is {@code buy}/{@code sell} and {@code side} is {@code
 * yes}/{@code no}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiOrder(
    @JsonProperty("order_id") String orderId,
    @JsonProperty("client_order_id") String clientOrderId,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("action") String action,
    @JsonProperty("side") String side,
    @JsonProperty("status") String status,
    @JsonProperty("yes_price") Integer yesPrice,
    @JsonProperty("no_price") Integer noPrice,
    @JsonProperty("initial_count") Integer initialCount,
    @JsonProperty("fill_count") Integer fillCount,
    @JsonProperty("remaining_count") Integer remainingCount,
    @JsonProperty("created_time") String createdTime) {}
