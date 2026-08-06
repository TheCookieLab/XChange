package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Single-market response envelope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiMarketResponse(@JsonProperty("market") KalshiMarket market) {}
