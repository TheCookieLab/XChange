package org.knowm.xchange.coinbase.cde;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw documented CDE REST historical funding schema, distinct from website observations.
 *
 * <p>Numeric strings and timestamp text are retained without conversion or settlement inference.
 * The REST schema has no finality flag or rate-unit description. The UDP final/predicted flags are
 * not present in this schema and must not be fabricated for these rows.
 *
 * @param symbol CDE instrument symbol
 * @param fundingRate funding-rate text, with delivered precision
 * @param futureMarkPrice futures mark price text
 * @param spotMarkPrice spot mark price text
 * @param fairValuePrice fair value price text
 * @param eventTime provider event timestamp, shown in the schema as ISO-8601 UTC
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseCdeHistoricalFundingRate(
    @JsonProperty("symbol") String symbol,
    @JsonProperty("funding_rate") String fundingRate,
    @JsonProperty("future_mark_price") String futureMarkPrice,
    @JsonProperty("spot_mark_price") String spotMarkPrice,
    @JsonProperty("fair_value_price") String fairValuePrice,
    @JsonProperty("event_time") String eventTime) {}
