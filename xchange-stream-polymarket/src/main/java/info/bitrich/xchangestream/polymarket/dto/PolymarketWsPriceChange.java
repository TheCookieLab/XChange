package info.bitrich.xchangestream.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Price-change event ({@code event_type=price_change}) on the Polymarket market channel. Each
 * change carries the <em>absolute</em> new size of one level of one outcome token (not a delta), so
 * re-applying a change is idempotent; a zero size removes the level.
 *
 * @param market condition id
 * @param timestamp event time in unix milliseconds (string-encoded)
 * @param priceChanges level updates, usually a single entry per subscribed token
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketWsPriceChange(
    @JsonProperty("market") String market,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("price_changes") List<Change> priceChanges) {

  /**
   * One absolute level update inside a {@link PolymarketWsPriceChange}.
   *
   * @param assetId outcome-token id the update belongs to
   * @param price dollars per share
   * @param size new absolute shares at the level; zero removes the level
   * @param side {@code BUY} for the bid side, {@code SELL} for the ask side
   * @param hash provider book hash
   * @param bestBid best bid after the update
   * @param bestAsk best ask after the update
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Change(
      @JsonProperty("asset_id") String assetId,
      @JsonProperty("price") String price,
      @JsonProperty("size") String size,
      @JsonProperty("side") String side,
      @JsonProperty("hash") String hash,
      @JsonProperty("best_bid") String bestBid,
      @JsonProperty("best_ask") String bestAsk) {}
}
