package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 order-book push payload ({@code {a, b, pseq, seq, maxDepth, ts}}).
 *
 * <p>The first push after (re)subscribing is a full {@code snapshot} ({@code action=snapshot});
 * subsequent pushes for the {@code books} topic are incremental {@code update}s carrying {@code
 * pseq} (sequence of the previous push) and a monotonic {@code seq}. The {@code books1}/{@code
 * books5}/{@code books50} topics always push full snapshots (replace-only) and never carry
 * incremental updates. There is no provider checksum in the v3 protocol; {@link
 * info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3OrderBookAssembler} relies on the {@code
 * pseq}/{@code seq} continuity rules documented by Bitget and resubscribes for a fresh snapshot on
 * any gap.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3OrderBookData {

  /** Asks; wire key {@code a}. */
  @JsonProperty("a")
  private List<BitgetUtaV3OrderBookLevel> asks;

  /** Bids; wire key {@code b}. */
  @JsonProperty("b")
  private List<BitgetUtaV3OrderBookLevel> bids;

  /** Sequence of the previous order-book push (continuity anchor for updates). */
  @JsonProperty("pseq")
  private Long pseq;

  /** Monotonic order-book sequence. */
  @JsonProperty("seq")
  private Long seq;

  /** Maximum depth of this order-book channel ({@code books} = full depth). */
  @JsonProperty("maxDepth")
  private Integer maxDepth;

  /** Provider timestamp in milliseconds. */
  @JsonProperty("ts")
  private Long ts;
}
