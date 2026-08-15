package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** MEXC Spot v3 order book snapshot from {@code GET /api/v3/depth}. */
public class MexcV3Depth {

  private final long lastUpdateId;
  private final List<MexcV3PriceLevel> bids;
  private final List<MexcV3PriceLevel> asks;

  public MexcV3Depth(
      @JsonProperty("lastUpdateId") long lastUpdateId,
      @JsonProperty("bids") List<MexcV3PriceLevel> bids,
      @JsonProperty("asks") List<MexcV3PriceLevel> asks) {
    this.lastUpdateId = lastUpdateId;
    this.bids = bids;
    this.asks = asks;
  }

  /** Monotonic book identifier; the reference point for delta-stream reconciliation. */
  public long getLastUpdateId() {
    return lastUpdateId;
  }

  public List<MexcV3PriceLevel> getBids() {
    return bids;
  }

  public List<MexcV3PriceLevel> getAsks() {
    return asks;
  }
}
