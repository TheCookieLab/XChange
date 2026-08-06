package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Create-order response. {@code orderID} is the provider order hash; {@code status} is {@code
 * live}, {@code matched}, or {@code delayed}. Amount fields echo the accepted making/taking
 * amounts in 6-decimal fixed-point micro-units (see
 * https://docs.polymarket.com/api-reference/trade/post-a-new-order).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketPostOrderResponse(
    @JsonProperty("success") Boolean success,
    @JsonProperty("errorMsg") String errorMsg,
    @JsonProperty("orderID") String orderId,
    @JsonProperty("status") String status,
    @JsonProperty("makingAmount") String makingAmount,
    @JsonProperty("takingAmount") String takingAmount,
    @JsonProperty("transactionsHashes") List<String> transactionsHashes,
    @JsonProperty("tradeIDs") List<String> tradeIds) {}
