package info.bitrich.xchangestream.coinbasederivatives;

/** Signals that a channel sequence cannot safely be continued without a fresh snapshot. */
public final class CoinbaseDerivativesStreamGapException
    extends CoinbaseDerivativesStreamException {

  private final String channel;
  private final long expectedPreviousChangeId;
  private final long actualPreviousChangeId;

  public CoinbaseDerivativesStreamGapException(
      String channel, long expectedPreviousChangeId, long actualPreviousChangeId) {
    super(
        "Coinbase derivatives stream gap on "
            + channel
            + ": expected prev_change_id="
            + expectedPreviousChangeId
            + ", received "
            + actualPreviousChangeId);
    this.channel = channel;
    this.expectedPreviousChangeId = expectedPreviousChangeId;
    this.actualPreviousChangeId = actualPreviousChangeId;
  }

  public String getChannel() {
    return channel;
  }

  public long getExpectedPreviousChangeId() {
    return expectedPreviousChangeId;
  }

  public long getActualPreviousChangeId() {
    return actualPreviousChangeId;
  }
}
