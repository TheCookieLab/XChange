package org.knowm.xchange.uniswap.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import org.knowm.xchange.currency.CurrencyPair;

/**
 * An amount-specific Uniswap v4 quote observed at one block.
 *
 * @param instrument the quoted pair
 * @param exactInput true for an exact-input quote, false for exact-output
 * @param amountIn human-readable input amount
 * @param amountOut human-readable output amount
 * @param amountInRaw smallest-unit input amount
 * @param amountOutRaw smallest-unit output amount
 * @param blockNumber the block the quote was simulated at
 * @param quotedAt when the quote was taken
 * @param gasEstimate estimated swap gas from the Quoter, when reported
 */
public record UniswapQuote(
    CurrencyPair instrument,
    boolean exactInput,
    BigDecimal amountIn,
    BigDecimal amountOut,
    BigInteger amountInRaw,
    BigInteger amountOutRaw,
    BigInteger blockNumber,
    Instant quotedAt,
    BigInteger gasEstimate) {

  /**
   * Effective price in quote currency per base unit, direction-aware: an exact-output quote's price
   * is {@code amountIn / amountOut} so both sides quote the same convention.
   */
  public BigDecimal price() {
    if (amountOut.signum() == 0 || amountIn.signum() == 0) {
      return null;
    }
    return exactInput
        ? amountOut.divide(amountIn, 18, java.math.RoundingMode.HALF_UP)
        : amountIn.divide(amountOut, 18, java.math.RoundingMode.HALF_UP);
  }

  /** Age of the quote in seconds. */
  public long ageSeconds() {
    return java.time.Duration.between(quotedAt, Instant.now()).getSeconds();
  }
}
