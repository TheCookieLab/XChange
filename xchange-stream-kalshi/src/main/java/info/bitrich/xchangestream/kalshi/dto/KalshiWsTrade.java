package info.bitrich.xchangestream.kalshi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kalshi WebSocket {@code trade} message payload: one public trade. Prices are dollar strings;
 * {@code takerSide} is {@code yes} or {@code no}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiWsTrade(
    @JsonProperty("trade_id") String tradeId,
    @JsonProperty("market_ticker") String marketTicker,
    @JsonProperty("yes_price_dollars") String yesPriceDollars,
    @JsonProperty("count_fp") String countFp,
    @JsonProperty("taker_side") String takerSide,
    @JsonProperty("ts_ms") Long tsMs) {}
