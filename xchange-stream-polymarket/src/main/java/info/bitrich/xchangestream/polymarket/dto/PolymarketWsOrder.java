package info.bitrich.xchangestream.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Order event ({@code event_type=order}) on the authenticated Polymarket user channel. The nested
 * {@code type} distinguishes {@code PLACEMENT}, {@code UPDATE}, and {@code CANCELLATION}; all three
 * carry the full current order state, so they adapt uniformly.
 *
 * @param id order id
 * @param market condition id
 * @param assetId outcome-token id the order trades
 * @param side {@code BUY} or {@code SELL} on the outcome token
 * @param originalSize original shares
 * @param sizeMatched cumulatively matched shares
 * @param price dollars per share
 * @param type lifecycle sub-type ({@code PLACEMENT}/{@code UPDATE}/{@code CANCELLATION})
 * @param status wire status ({@code LIVE}/{@code MATCHED}/{@code CANCELED}/{@code DELAYED})
 * @param orderType {@code GTC}/{@code FOK}/{@code FAK}/{@code GTD}
 * @param createdAt creation time in unix seconds (string-encoded)
 * @param timestamp event time in unix milliseconds (string-encoded)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketWsOrder(
    @JsonProperty("id") String id,
    @JsonProperty("market") String market,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("side") String side,
    @JsonProperty("original_size") String originalSize,
    @JsonProperty("size_matched") String sizeMatched,
    @JsonProperty("price") String price,
    @JsonProperty("type") String type,
    @JsonProperty("status") String status,
    @JsonProperty("order_type") String orderType,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("timestamp") String timestamp) {}
