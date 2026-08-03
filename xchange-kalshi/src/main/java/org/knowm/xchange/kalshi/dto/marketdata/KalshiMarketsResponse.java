package org.knowm.xchange.kalshi.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paginated markets response envelope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiMarketsResponse(
    @JsonProperty("markets") List<KalshiMarket> markets,
    @JsonProperty("cursor") String cursor) {}
