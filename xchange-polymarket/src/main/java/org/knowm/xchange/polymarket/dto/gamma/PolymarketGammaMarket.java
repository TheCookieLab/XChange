package org.knowm.xchange.polymarket.dto.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Gamma market record. {@code outcomes}, {@code outcomePrices}, and {@code clobTokenIds} are
 * stringified JSON arrays (provider quirk); index 0 is the primary outcome.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketGammaMarket(
    @JsonProperty("id") String id,
    @JsonProperty("conditionId") String conditionId,
    @JsonProperty("question") String question,
    @JsonProperty("outcomes") String outcomes,
    @JsonProperty("outcomePrices") String outcomePrices,
    @JsonProperty("clobTokenIds") String clobTokenIds,
    @JsonProperty("active") Boolean active,
    @JsonProperty("closed") Boolean closed,
    @JsonProperty("enableOrderBook") Boolean enableOrderBook,
    @JsonProperty("orderMinSize") BigDecimal orderMinSize,
    @JsonProperty("orderPriceMinTickSize") BigDecimal orderPriceMinTickSize,
    @JsonProperty("volume") String volume,
    @JsonProperty("negRisk") Boolean negRisk) {}
