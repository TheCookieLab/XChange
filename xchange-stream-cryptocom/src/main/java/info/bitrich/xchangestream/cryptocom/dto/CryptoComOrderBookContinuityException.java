package info.bitrich.xchangestream.cryptocom.dto;

import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Dedicated order-book continuity failure: the snapshot/increment sequence chain of a Crypto.com
 * book channel broke (a partial update referenced a previous update id that was never applied, or
 * the wait for the opening snapshot exceeded its buffer). The consumer must not trust the book
 * until a fresh snapshot rebuilds it; {@code CryptoComOrderBookAssembler} then recovers on the
 * next full snapshot and resumes emitting books.
 */
public class CryptoComOrderBookContinuityException extends ExchangeException {

  private static final long serialVersionUID = 1L;

  private final String channel;

  /** Last applied update id ({@code u}); {@code null} when no snapshot was ever applied. */
  private final Long lastAppliedSequence;

  /** Update id of the offending dataframe. */
  private final Long sequence;

  /** Previous update id the offending dataframe required. */
  private final Long previousSequence;

  public CryptoComOrderBookContinuityException(
      String channel, Long lastAppliedSequence, Long sequence, Long previousSequence) {
    super(
        describe(
            channel, lastAppliedSequence, sequence, previousSequence));
    this.channel = channel;
    this.lastAppliedSequence = lastAppliedSequence;
    this.sequence = sequence;
    this.previousSequence = previousSequence;
  }

  private static String describe(
      String channel, Long lastAppliedSequence, Long sequence, Long previousSequence) {
    StringBuilder sb = new StringBuilder("Crypto.com order book sequence gap on ").append(channel);
    if (lastAppliedSequence != null) {
      sb.append(": last applied u=").append(lastAppliedSequence);
    } else {
      sb.append(": no snapshot applied yet");
    }
    if (sequence != null) {
      sb.append(", offending u=").append(sequence);
    }
    if (previousSequence != null) {
      sb.append(", required pu=").append(previousSequence);
    }
    sb.append("; waiting for a fresh full snapshot to rebuild");
    return sb.toString();
  }

  /** Book channel the failure belongs to, e.g. {@code book.BTC_USDT.10}. */
  public String getChannel() {
    return channel;
  }

  /** Last applied update id ({@code u}); {@code null} when no snapshot was ever applied. */
  public Long getLastAppliedSequence() {
    return lastAppliedSequence;
  }

  /** Update id of the offending dataframe. */
  public Long getSequence() {
    return sequence;
  }

  /** Previous update id the offending dataframe required. */
  public Long getPreviousSequence() {
    return previousSequence;
  }
}