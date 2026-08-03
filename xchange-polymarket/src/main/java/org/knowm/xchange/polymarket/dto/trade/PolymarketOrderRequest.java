package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Create-order request body: the signed order, the L2 API key id as {@code owner}, and the order
 * type ({@code GTC}, {@code FOK}, {@code GTD}, or {@code FAK}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolymarketOrderRequest(
    @JsonProperty("order") PolymarketSignedOrder order,
    @JsonProperty("owner") String owner,
    @JsonProperty("orderType") String orderType,
    @JsonProperty("postOnly") Boolean postOnly,
    @JsonProperty("deferExec") Boolean deferExec) {}
