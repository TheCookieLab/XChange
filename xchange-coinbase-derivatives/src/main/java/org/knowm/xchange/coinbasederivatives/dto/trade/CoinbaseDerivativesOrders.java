package org.knowm.xchange.coinbasederivatives.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paged order result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesOrders(
    List<CoinbaseDerivativesOrder> orders, @JsonProperty("has_more") boolean hasMore) {}
