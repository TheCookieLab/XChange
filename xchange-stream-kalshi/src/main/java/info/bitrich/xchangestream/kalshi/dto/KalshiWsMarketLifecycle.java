package info.bitrich.xchangestream.kalshi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kalshi WebSocket {@code market_lifecycle_v2} message payload: a market status transition.
 * {@code eventType} is one of {@code created}, {@code activated}, {@code deactivated}, {@code
 * close_date_updated}, {@code determined}, {@code settled}, {@code price_level_structure_updated},
 * {@code metadata_updated}; the remaining fields appear only for the event types Kalshi documents
 * them for. Timestamps are unix seconds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiWsMarketLifecycle(
    @JsonProperty("market_ticker") String marketTicker,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("result") String result,
    @JsonProperty("settlement_value") String settlementValue,
    @JsonProperty("open_ts") Long openTs,
    @JsonProperty("close_ts") Long closeTs,
    @JsonProperty("determination_ts") Long determinationTs,
    @JsonProperty("settled_ts") Long settledTs,
    @JsonProperty("is_deactivated") Boolean isDeactivated) {}
