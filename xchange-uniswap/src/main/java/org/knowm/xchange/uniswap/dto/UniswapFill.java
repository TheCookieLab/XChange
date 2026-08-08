package org.knowm.xchange.uniswap.dto;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * One decoded leg of an on-chain swap: the pool-side amount of a currency, signed by direction
 * (negative = paid, positive = received).
 */
public record UniswapFill(String currencySymbol, BigDecimal amount, BigInteger blockNumber) {

  /** Raw signed pool delta this fill was decoded from. */
  public static UniswapFill of(String currencySymbol, java.math.BigInteger rawAmount, int decimals, BigInteger blockNumber) {
    return new UniswapFill(currencySymbol, new BigDecimal(rawAmount).movePointLeft(decimals), blockNumber);
  }
}
