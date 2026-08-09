package info.bitrich.xchangestream.coinbase.dto;

import java.util.Objects;
import org.knowm.xchange.currency.CurrencyPair;

/**
 * A detected sequence discontinuity in a Coinbase level2 order book stream.
 *
 * <p>Emitted when an update arrives whose sequence jumps past the expected next sequence. If
 * {@code recovered} is true the book was rebuilt from a REST snapshot and streaming continues;
 * otherwise the book is stale and subscribers must refetch or resubscribe. The event is the
 * failure surface: a gap is never silently swallowed.
 */
public final class CoinbaseOrderBookGap {

  private final CurrencyPair currencyPair;
  private final long expectedSequence;
  private final long receivedSequence;
  private final boolean recovered;
  private final long timestampMillis;

  public CoinbaseOrderBookGap(
      CurrencyPair currencyPair,
      long expectedSequence,
      long receivedSequence,
      boolean recovered) {
    this(currencyPair, expectedSequence, receivedSequence, recovered, System.currentTimeMillis());
  }

  public CoinbaseOrderBookGap(
      CurrencyPair currencyPair,
      long expectedSequence,
      long receivedSequence,
      boolean recovered,
      long timestampMillis) {
    this.currencyPair = currencyPair;
    this.expectedSequence = expectedSequence;
    this.receivedSequence = receivedSequence;
    this.recovered = recovered;
    this.timestampMillis = timestampMillis;
  }

  /** The order book whose stream was discontinuous. */
  public CurrencyPair getCurrencyPair() {
    return currencyPair;
  }

  /** The next sequence number that was expected. */
  public long getExpectedSequence() {
    return expectedSequence;
  }

  /** The sequence number that was actually received. */
  public long getReceivedSequence() {
    return receivedSequence;
  }

  /** Whether the book was rebuilt from a REST snapshot after the gap. */
  public boolean isRecovered() {
    return recovered;
  }

  /** When the gap was detected (epoch milliseconds). */
  public long getTimestampMillis() {
    return timestampMillis;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CoinbaseOrderBookGap)) {
      return false;
    }
    CoinbaseOrderBookGap gap = (CoinbaseOrderBookGap) other;
    return expectedSequence == gap.expectedSequence
        && receivedSequence == gap.receivedSequence
        && recovered == gap.recovered
        && Objects.equals(currencyPair, gap.currencyPair);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currencyPair, expectedSequence, receivedSequence, recovered);
  }

  @Override
  public String toString() {
    return "CoinbaseOrderBookGap [currencyPair=" + currencyPair
        + ", expectedSequence=" + expectedSequence
        + ", receivedSequence=" + receivedSequence
        + ", recovered=" + recovered + "]";
  }
}
