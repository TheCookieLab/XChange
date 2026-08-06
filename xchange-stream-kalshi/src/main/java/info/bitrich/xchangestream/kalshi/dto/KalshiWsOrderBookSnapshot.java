package info.bitrich.xchangestream.kalshi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Kalshi WebSocket {@code orderbook_snapshot} message payload: the complete aggregated book for
 * one market. The module subscribes with {@code use_yes_price: true}, so both sides arrive on the
 * unified yes-leg price scale. Price levels are {@code [price_dollars, contract_count_fp]} string
 * pairs, e.g. {@code ["0.0800", "300.00"]}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiWsOrderBookSnapshot(
    @JsonProperty("market_ticker") String marketTicker,
    @JsonProperty("market_id") String marketId,
    @JsonProperty("yes_dollars_fp") List<List<String>> yesDollarsFp,
    @JsonProperty("no_dollars_fp") List<List<String>> noDollarsFp) {}
