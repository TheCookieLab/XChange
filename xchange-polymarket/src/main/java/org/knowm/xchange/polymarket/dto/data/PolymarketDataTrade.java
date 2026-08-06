package org.knowm.xchange.polymarket.dto.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Public Data-API trade; {@code timestamp} is unix seconds and {@code asset} the token id. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketDataTrade(
    @JsonProperty("proxyWallet") String proxyWallet,
    @JsonProperty("side") String side,
    @JsonProperty("asset") String asset,
    @JsonProperty("conditionId") String conditionId,
    @JsonProperty("size") BigDecimal size,
    @JsonProperty("price") BigDecimal price,
    @JsonProperty("timestamp") Long timestamp,
    @JsonProperty("title") String title,
    @JsonProperty("outcome") String outcome,
    @JsonProperty("outcomeIndex") Integer outcomeIndex,
    @JsonProperty("transactionHash") String transactionHash) {}
