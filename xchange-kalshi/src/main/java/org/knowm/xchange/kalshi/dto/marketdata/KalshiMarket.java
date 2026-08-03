package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kalshi market record. Prices ({@code yesBid}, {@code yesAsk}, {@code lastPrice}) are integer
 * cents in the range 1..99.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiMarket(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("event_ticker") String eventTicker,
    @JsonProperty("title") String title,
    @JsonProperty("status") String status,
    @JsonProperty("yes_bid") Integer yesBid,
    @JsonProperty("yes_ask") Integer yesAsk,
    @JsonProperty("last_price") Integer lastPrice,
    @JsonProperty("volume") Long volume,
    @JsonProperty("open_interest") Long openInterest) {}
