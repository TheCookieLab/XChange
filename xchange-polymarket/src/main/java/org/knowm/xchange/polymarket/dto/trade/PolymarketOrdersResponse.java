package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One page of the authenticated {@code GET /data/orders} response. CLOB V2 paginates with {@code
 * next_cursor}: an empty value means no further pages (see
 * https://docs.polymarket.com/api-reference/trade/get-user-orders).
 *
 * @param limit maximum number of results per page
 * @param nextCursor base64-encoded cursor for the next page; blank when the page is last
 * @param count number of orders on this page
 * @param data orders on this page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketOrdersResponse(
    @JsonProperty("limit") Integer limit,
    @JsonProperty("next_cursor") String nextCursor,
    @JsonProperty("count") Integer count,
    @JsonProperty("data") List<PolymarketOpenOrder> data) {}
