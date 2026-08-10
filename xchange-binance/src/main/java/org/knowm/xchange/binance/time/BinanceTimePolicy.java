package org.knowm.xchange.binance.time;

import org.knowm.xchange.binance.config.BinanceConfiguration;
import org.knowm.xchange.binance.config.BinanceTimestampUnit;

/**
 * Central policy for Binance request timestamps.
 *
 * <p>Policy: signed requests carry a {@code timestamp} in the configured unit ({@link
 * BinanceTimestampUnit#MILLISECONDS} by default; {@link BinanceTimestampUnit#MICROSECONDS} for
 * futures families that accept it). The timestamp is generated from the local clock, and the
 * server-time delta measured by {@link org.knowm.xchange.binance.BinanceTimestampFactory} is
 * applied by callers that synchronize. The receive window is validated against Binance's
 * {@code [0, 60000]} ms range before the first network call.
 */
public final class BinanceTimePolicy {

  private BinanceTimePolicy() {}

  /** Maximum receive window Binance accepts, in milliseconds. */
  public static final long MAX_RECV_WINDOW_MS = BinanceConfiguration.MAX_RECV_WINDOW_MS;

  /** Current local time in milliseconds. */
  public static long currentTimestampMillis() {
    return System.currentTimeMillis();
  }

  /** Converts a millisecond timestamp to the configured unit. */
  public static long applyUnit(long timestampMillis, BinanceTimestampUnit unit) {
    return unit == BinanceTimestampUnit.MICROSECONDS ? timestampMillis * 1000L : timestampMillis;
  }

  /**
   * @throws IllegalArgumentException when the receive window is outside Binance's accepted range.
   */
  public static void validateRecvWindow(Long recvWindow) {
    if (recvWindow != null && (recvWindow < 0 || recvWindow > MAX_RECV_WINDOW_MS)) {
      throw new IllegalArgumentException(
          "Binance receive window must be in the range [0, "
              + MAX_RECV_WINDOW_MS
              + "], got "
              + recvWindow
              + ".");
    }
  }
}
