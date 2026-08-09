package info.bitrich.xchangestream.kraken.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Order book message (snapshot or incremental update) on the {@code book} channel.
 *
 * <p>A snapshot carries the full book; an update carries per-level deltas where a quantity of
 * zero removes the level. Both may carry a checksum of the top 10 price levels per side (see
 * {@link info.bitrich.xchangestream.kraken.KrakenStreamingAdapters#checksum}).
 */
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class KrakenBookMessage extends KrakenDataMessage<KrakenBookMessage.KrakenBookLevels> {

  @Override
  public String getChannelId() {
    KrakenBookLevels payload = getPayload();
    if (payload == null || payload.getSymbol() == null) {
      return super.getChannelId();
    }
    return super.getChannelId() + "_" + payload.getSymbol();
  }

  /** One book data entry: symbol, bid/ask level lists, and the optional checksum. */
  @Data
  @Builder
  @Jacksonized
  public static class KrakenBookLevels {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("bids")
    private List<List<String>> bids;

    @JsonProperty("asks")
    private List<List<String>> asks;

    @JsonProperty("checksum")
    private Long checksum;

    @JsonProperty("timestamp")
    private String timestamp;

    /** @return the checksum of the top levels as sent by the provider, or {@code null} */
    public Long getChecksum() {
      return checksum;
    }
  }

  /**
   * One order book level.
   *
   * @param price level price
   * @param quantity level quantity (zero removes the level in an update)
   */
  public record KrakenBookLevel(BigDecimal price, BigDecimal quantity) {}
}
