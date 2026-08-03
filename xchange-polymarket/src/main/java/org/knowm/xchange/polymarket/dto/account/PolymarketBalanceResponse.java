package org.knowm.xchange.polymarket.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Collateral balance response; {@code balance} is a 6-decimal fixed-point USDC string. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketBalanceResponse(
    @JsonProperty("balance") String balance,
    @JsonProperty("allowances") Map<String, String> allowances) {}
