package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Cancel response envelope carrying the updated order. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiCancelResponse(@JsonProperty("order") KalshiOrder order) {}
