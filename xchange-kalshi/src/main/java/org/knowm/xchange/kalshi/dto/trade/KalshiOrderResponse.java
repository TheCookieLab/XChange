package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Single-order response envelope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiOrderResponse(@JsonProperty("order") KalshiOrder order) {}
