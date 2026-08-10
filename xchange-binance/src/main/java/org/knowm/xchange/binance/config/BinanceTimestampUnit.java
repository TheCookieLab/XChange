package org.knowm.xchange.binance.config;

/**
 * Timestamp unit used for the {@code timestamp} parameter of signed requests.
 *
 * <p>Binance accepts millisecond timestamps on the classic endpoints; the USDⓈ-M and COIN-M
 * futures families additionally accept microsecond timestamps. The unit is selected per
 * configuration and applied centrally by the timestamp policy so that drift handling and
 * signatures stay consistent.
 */
public enum BinanceTimestampUnit {
  MILLISECONDS,
  MICROSECONDS
}
