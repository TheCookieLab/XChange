package org.knowm.xchange.polymarket.dto.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Keyset page of Gamma markets; {@code nextCursor} is opaque and blank on the last page. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketGammaMarketsKeysetResponse(
    @JsonProperty("markets") List<PolymarketGammaMarket> markets,
    @JsonProperty("next_cursor") String nextCursor) {}
