package org.knowm.xchange.bitget.uta.v3.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 order book.
 *
 * <p>{@code GET /api/v3/market/orderbook?symbol=...&category=...&limit=...} returns levels as
 * arrays of {@code [price, quantity]} decimal strings: {@code a} = asks ascending, {@code b} = bids
 * descending, plus a millisecond {@code ts}. The REST payload carries no checksum — the checksum
 * exists only on the WebSocket order-book channel.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3OrderBook {

  @JsonProperty("a")
  private List<BigDecimal[]> asks;

  @JsonProperty("b")
  private List<BigDecimal[]> bids;

  @JsonProperty("ts")
  private Long ts;
}
