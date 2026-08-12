package info.bitrich.xchangestream.bitget.uta.v3;

/**
 * Dedicated continuity failure for the UTA v3 order-book stream.
 *
 * <p>Raised by {@link BitgetUtaV3OrderBookAssembler} when an incremental update breaks sequence
 * continuity (gap, previous-sequence mismatch, or provider sequence-space reset). The consumer must
 * not trust the local book afterwards: the channel is resubscribed and the assembler rebuilt from a
 * fresh snapshot. Emitted as the dedicated failure on {@link
 * BitgetUtaV3StreamingMarketDataService#subscribeOrderBookContinuityFailures()} so stream consumers
 * can react without the order-book observable itself erroring out.
 *
 * @since 5.1.0
 */
public class BitgetUtaV3OrderBookContinuityException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String subscriptionId;

  public BitgetUtaV3OrderBookContinuityException(String subscriptionId, String reason) {
    super(
        "Bitget UTA v3 order-book continuity lost for "
            + subscriptionId
            + ": "
            + reason
            + "; resubscribing for a fresh snapshot");
    this.subscriptionId = subscriptionId;
  }

  /** Subscription id of the affected order-book channel. */
  public String getSubscriptionId() {
    return subscriptionId;
  }
}
