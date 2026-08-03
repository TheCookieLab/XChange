package info.bitrich.xchangestream.kalshi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kalshi WebSocket {@code orderbook_delta} message payload: a signed contract-count change at one
 * price level of one side. {@code deltaFp} is a signed fixed-point string (2 decimals); a level is
 * removed when its running count reaches zero.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiWsOrderBookDelta(
    @JsonProperty("market_ticker") String marketTicker,
    @JsonProperty("price_dollars") String priceDollars,
    @JsonProperty("delta_fp") String deltaFp,
    @JsonProperty("side") String side,
    @JsonProperty("ts_ms") Long tsMs) {}
