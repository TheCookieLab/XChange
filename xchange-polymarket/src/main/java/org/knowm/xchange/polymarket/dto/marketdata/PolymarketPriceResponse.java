package org.knowm.xchange.polymarket.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Executable price for one side of one outcome token. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketPriceResponse(@JsonProperty("price") String price) {}
