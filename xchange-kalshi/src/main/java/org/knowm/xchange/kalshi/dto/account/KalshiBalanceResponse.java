package org.knowm.xchange.kalshi.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Portfolio balance; {@code balance} is available cash in integer cents. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiBalanceResponse(
    @JsonProperty("balance") Long balance,
    @JsonProperty("payout") Long payout) {}
