package org.knowm.xchange.kalshi.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Order record as returned by the {@code GET /portfolio/orders} read surface. Direction is the
 * canonical {@code bookSide} ({@code bid} = buy YES, {@code ask} = sell YES); {@code
 * yesPriceDollars} is a fixed-point dollar string quoted on the YES leg and the count fields are
 * fixed-point count strings with 2 decimals.
 *
 * @see <a href="https://docs.kalshi.com/api-reference/orders/get-orders">Kalshi Get Orders</a>
 * @see <a href="https://docs.kalshi.com/getting_started/order_direction">Kalshi order
 *     direction</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiOrder(
    @JsonProperty("order_id") String orderId,
    @JsonProperty("client_order_id") String clientOrderId,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("book_side") String bookSide,
    @JsonProperty("status") String status,
    @JsonProperty("yes_price_dollars") String yesPriceDollars,
    @JsonProperty("initial_count_fp") String initialCountFp,
    @JsonProperty("fill_count_fp") String fillCountFp,
    @JsonProperty("remaining_count_fp") String remainingCountFp,
    @JsonProperty("created_time") String createdTime) {}
