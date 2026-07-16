package org.knowm.xchange.coinbasederivatives.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** Full order-book snapshot. Each level is provider ordered price and amount. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesOrderBook(
    @JsonProperty("instrument_name") String instrumentName,
    long timestamp,
    @JsonProperty("change_id") Long changeId,
    List<List<BigDecimal>> bids,
    List<List<BigDecimal>> asks) {}
