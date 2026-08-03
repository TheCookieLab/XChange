package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paginated orders response envelope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiOrdersResponse(
    @JsonProperty("orders") List<KalshiOrder> orders,
    @JsonProperty("cursor") String cursor) {}
