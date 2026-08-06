package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Cancel request body naming the provider order id. */
public record PolymarketCancelRequest(@JsonProperty("orderID") String orderId) {}
