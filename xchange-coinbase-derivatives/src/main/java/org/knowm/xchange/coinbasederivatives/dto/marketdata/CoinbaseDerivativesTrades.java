package org.knowm.xchange.coinbasederivatives.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paged public-trades result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesTrades(
    List<CoinbaseDerivativesTrade> trades, @JsonProperty("has_more") boolean hasMore) {}
