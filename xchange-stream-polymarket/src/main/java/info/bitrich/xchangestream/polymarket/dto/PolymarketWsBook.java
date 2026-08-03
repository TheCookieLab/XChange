package info.bitrich.xchangestream.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Full order-book event ({@code event_type=book}) on the Polymarket market channel. Carries the
 * complete level set for one outcome token; levels arrive worst-first.
 *
 * @param market condition id
 * @param assetId outcome-token id the book belongs to
 * @param timestamp event time in unix milliseconds (string-encoded)
 * @param hash provider book hash (ordering aid, not a sequence number)
 * @param bids bid levels, worst-first
 * @param asks ask levels, worst-first
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketWsBook(
    @JsonProperty("market") String market,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("hash") String hash,
    @JsonProperty("bids") List<Level> bids,
    @JsonProperty("asks") List<Level> asks) {

  /**
   * One price level of a {@link PolymarketWsBook}.
   *
   * @param price dollars per share
   * @param size shares available at the level
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Level(@JsonProperty("price") String price, @JsonProperty("size") String size) {}
}
