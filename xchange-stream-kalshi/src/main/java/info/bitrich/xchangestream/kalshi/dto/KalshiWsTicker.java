package info.bitrich.xchangestream.kalshi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kalshi WebSocket {@code ticker} message payload: top-of-book and session statistics for one
 * market. Prices are dollar strings; {@code volumeFp} is a fixed-point contract count.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiWsTicker(
    @JsonProperty("market_ticker") String marketTicker,
    @JsonProperty("price_dollars") String priceDollars,
    @JsonProperty("yes_bid_dollars") String yesBidDollars,
    @JsonProperty("yes_ask_dollars") String yesAskDollars,
    @JsonProperty("volume_fp") String volumeFp,
    @JsonProperty("ts_ms") Long tsMs) {}
