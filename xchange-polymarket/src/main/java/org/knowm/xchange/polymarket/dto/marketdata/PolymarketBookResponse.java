package org.knowm.xchange.polymarket.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** CLOB order book summary for one outcome token; bids and asks arrive worst-first. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketBookResponse(
    @JsonProperty("market") String market,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("hash") String hash,
    @JsonProperty("bids") List<PolymarketBookLevel> bids,
    @JsonProperty("asks") List<PolymarketBookLevel> asks) {

  /** One price level; {@code price} is dollars per share and {@code size} is shares. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PolymarketBookLevel(
      @JsonProperty("price") String price, @JsonProperty("size") String size) {}
}
