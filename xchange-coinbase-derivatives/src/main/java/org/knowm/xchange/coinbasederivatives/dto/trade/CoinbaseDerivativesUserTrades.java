package org.knowm.xchange.coinbasederivatives.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paged private-fill result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesUserTrades(
    List<CoinbaseDerivativesUserTrade> trades, @JsonProperty("has_more") boolean hasMore) {}
