package info.bitrich.xchangestream.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Last-trade event ({@code event_type=last_trade_price}) on the Polymarket market channel.
 *
 * @param market condition id
 * @param assetId outcome-token id traded
 * @param price dollars per share
 * @param size shares traded
 * @param feeRateBps taker fee rate in basis points
 * @param side aggressor side: {@code BUY} lifted the ask, {@code SELL} hit the bid
 * @param timestamp event time in unix milliseconds (string-encoded)
 * @param transactionHash settlement transaction hash; {@code null} while the trade is off-chain
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketWsLastTradePrice(
    @JsonProperty("market") String market,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("price") String price,
    @JsonProperty("size") String size,
    @JsonProperty("fee_rate_bps") String feeRateBps,
    @JsonProperty("side") String side,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("transaction_hash") String transactionHash) {}
