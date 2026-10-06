package org.knowm.xchange.client.ratelimit;

/**
 * Service class of a rate-limited operation. The class is owned by the exchange module's policy;
 * callers cannot select it.
 *
 * <p>For requests contending on a common budget, {@link #EXECUTION} is always admitted before
 * {@link #MARKET_DATA}; within a class, admission is FIFO.
 *
 * @since 1.0.3
 */
public enum RateLimitPriority {
  /** Economic mutations and the order/position/account reads required for execution safety. */
  EXECUTION,
  /** Discovery, products, candles, tickers, books, time and other market data. */
  MARKET_DATA
}
