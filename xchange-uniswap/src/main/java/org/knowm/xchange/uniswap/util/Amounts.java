package org.knowm.xchange.uniswap.util;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Human-readable ↔ smallest-unit amount conversion for configured token decimals. */
public final class Amounts {

  /** Largest value that fits the v4 quoter/router {@code uint128} amount fields. */
  public static final BigInteger UINT128_MAX = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);

  private Amounts() {}

  /**
   * Scales a human-readable amount to token smallest units, rejecting amounts finer than the token
   * precision or larger than {@code uint128}.
   */
  public static BigInteger toRaw(BigDecimal amount, int decimals) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("amount must be positive: " + amount);
    }
    BigDecimal scaled = amount.movePointRight(decimals);
    BigInteger raw;
    try {
      raw = scaled.toBigIntegerExact();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "amount " + amount.toPlainString() + " exceeds token precision of " + decimals + " decimals", e);
    }
    if (raw.compareTo(UINT128_MAX) > 0) {
      throw new IllegalArgumentException("amount " + amount.toPlainString() + " exceeds uint128 range");
    }
    return raw;
  }

  /** Scales smallest units to a human-readable amount. */
  public static BigDecimal toHuman(BigInteger raw, int decimals) {
    return new BigDecimal(raw).movePointLeft(decimals);
  }
}
