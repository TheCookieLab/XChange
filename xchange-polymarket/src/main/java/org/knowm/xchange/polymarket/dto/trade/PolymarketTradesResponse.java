package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One page of the authenticated {@code GET /data/trades} response. CLOB V2 paginates with {@code
 * next_cursor}; the sentinel {@code LTE=} marks the last page (see
 * https://docs.polymarket.com/api-reference/trade/get-trades).
 *
 * @param limit maximum number of results per page
 * @param nextCursor base64-encoded cursor for the next page; {@code LTE=} or blank means the page
 *     is last
 * @param count number of trades on this page
 * @param data trades on this page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketTradesResponse(
    @JsonProperty("limit") Integer limit,
    @JsonProperty("next_cursor") String nextCursor,
    @JsonProperty("count") Integer count,
    @JsonProperty("data") List<PolymarketUserTrade> data) {}
