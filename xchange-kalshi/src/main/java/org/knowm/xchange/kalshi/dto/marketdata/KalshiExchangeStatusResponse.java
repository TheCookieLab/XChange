package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Exchange trading status. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiExchangeStatusResponse(
    @JsonProperty("trading_active") boolean tradingActive,
    @JsonProperty("exchange_active") boolean exchangeActive) {}
