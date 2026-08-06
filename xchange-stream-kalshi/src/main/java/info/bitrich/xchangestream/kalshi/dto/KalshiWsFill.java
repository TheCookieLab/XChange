package info.bitrich.xchangestream.kalshi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kalshi WebSocket {@code fill} message payload: one authenticated user fill. {@code side} is
 * {@code yes}/{@code no}, {@code action} is {@code buy}/{@code sell}, prices are dollar strings
 * and {@code feeCost} is a fixed-point dollar amount.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiWsFill(
    @JsonProperty("trade_id") String tradeId,
    @JsonProperty("order_id") String orderId,
    @JsonProperty("market_ticker") String marketTicker,
    @JsonProperty("is_taker") Boolean isTaker,
    @JsonProperty("side") String side,
    @JsonProperty("action") String action,
    @JsonProperty("yes_price_dollars") String yesPriceDollars,
    @JsonProperty("count_fp") String countFp,
    @JsonProperty("fee_cost") String feeCost,
    @JsonProperty("client_order_id") String clientOrderId,
    @JsonProperty("ts_ms") Long tsMs) {}
