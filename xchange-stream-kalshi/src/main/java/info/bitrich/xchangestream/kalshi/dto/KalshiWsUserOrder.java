package info.bitrich.xchangestream.kalshi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kalshi WebSocket {@code user_order} message payload: one authenticated order-state update.
 * {@code status} is {@code resting}/{@code canceled}/{@code executed}; {@code bookSide} is the
 * YES-book side the order rests on ({@code bid} = buying the YES outcome, {@code ask} = selling
 * it). Prices are 4-decimal dollar strings; counts are 2-decimal fixed-point strings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiWsUserOrder(
    @JsonProperty("order_id") String orderId,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("status") String status,
    @JsonProperty("side") String side,
    @JsonProperty("book_side") String bookSide,
    @JsonProperty("yes_price_dollars") String yesPriceDollars,
    @JsonProperty("fill_count_fp") String fillCountFp,
    @JsonProperty("remaining_count_fp") String remainingCountFp,
    @JsonProperty("initial_count_fp") String initialCountFp,
    @JsonProperty("client_order_id") String clientOrderId,
    @JsonProperty("created_ts_ms") Long createdTsMs) {}
