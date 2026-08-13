package info.bitrich.xchangestream.bybit.dto.marketdata;

import java.beans.ConstructorProperties;
import lombok.Getter;

/**
 * Dedicated failure signal for an unprovable order-book continuity gap.
 *
 * <p>Emitted by {@code BybitStreamingMarketDataService.getOrderBookGapEvents()} before the
 * affected channel is resubscribed and rebuilt from a fresh snapshot. Consumers can use it to
 * alert or reconcile; the stream itself recovers automatically.
 */
@Getter
public class BybitOrderBookGap {

  /** Channel identity, e.g. {@code orderbook.50.BTCUSDT}. */
  private final String channelUniqueId;

  /** Last locally applied update id; the next delta was expected to be {@code expectedU}. */
  private final long expectedU;

  /** Update id actually received. */
  private final long actualU;

  /** Machine-readable reason, e.g. {@code sequence} or {@code missing-snapshot}. */
  private final String reason;

  @ConstructorProperties({"channelUniqueId", "expectedU", "actualU", "reason"})
  public BybitOrderBookGap(
      String channelUniqueId, long expectedU, long actualU, String reason) {
    this.channelUniqueId = channelUniqueId;
    this.expectedU = expectedU;
    this.actualU = actualU;
    this.reason = reason;
  }
}
