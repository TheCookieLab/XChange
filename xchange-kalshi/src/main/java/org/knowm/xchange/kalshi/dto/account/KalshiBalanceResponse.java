package org.knowm.xchange.kalshi.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Portfolio balance ({@code GET /portfolio/balance}). {@code balanceDollars} is the canonical
 * available-cash fixed-point dollar string; {@code balance} and {@code portfolioValue} are the
 * legacy integer-cent views, still returned alongside it.
 *
 * @see <a href="https://docs.kalshi.com/api-reference/portfolio/get-balance">Kalshi Get
 *     Balance</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiBalanceResponse(
    @JsonProperty("balance_dollars") String balanceDollars,
    @JsonProperty("balance") Long balance,
    @JsonProperty("portfolio_value") Long portfolioValue,
    @JsonProperty("updated_ts") Long updatedTs) {}
