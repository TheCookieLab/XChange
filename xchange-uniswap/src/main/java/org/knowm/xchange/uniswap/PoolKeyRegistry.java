package org.knowm.xchange.uniswap;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.uniswap.protocol.UniswapAbiEncoder;
import org.knowm.xchange.uniswap.util.Addresses;

/**
 * Immutable registry of the configured Uniswap v4 pools.
 *
 * <p>Each pool is identified by a currency pair (base/quote symbols from the token registry) and
 * carries the full v4 {@code PoolKey} — the two sorted pool currencies, fee, tick spacing, and
 * hooks address. Pools are fail-closed: a pair that is not configured here cannot be traded or
 * quoted, and a pool that references unconfigured tokens is rejected at construction.
 */
public final class PoolKeyRegistry {

  /** Maximum allowed LP fee, and the dynamic-fee marker, from v4-core's PoolKey. */
  public static final int MAX_FEE = 1_000_000;
  public static final int DYNAMIC_FEE_FLAG = 0x800000;
  public static final int MAX_TICK_SPACING = Short.MAX_VALUE;

  /**
   * A configured v4 pool.
   *
   * @param baseSymbol base currency symbol of the pair (e.g. {@code ETH})
   * @param quoteSymbol quote currency symbol of the pair (e.g. {@code USDC})
   * @param currency0 lower pool currency address (address(currency0) &lt; address(currency1))
   * @param currency1 higher pool currency address
   * @param fee pool LP fee in hundredths of a bip, or the dynamic fee marker {@code 0x800000}
   * @param tickSpacing minimum tick interval of the pool
   * @param hooks hooks contract address, or the zero address for pools without hooks
   * @param baseAddress the pool currency address of the base side (derived, e.g. WETH for ETH/USDC)
   * @param quoteAddress the pool currency address of the quote side (derived)
   */
  public record PoolDefinition(
      String baseSymbol,
      String quoteSymbol,
      String currency0,
      String currency1,
      int fee,
      int tickSpacing,
      String hooks,
      String baseAddress,
      String quoteAddress) {

    /** The normalized base/quote pair key, e.g. {@code ETH/USDC}. */
    public String pairKey() {
      return baseSymbol + "/" + quoteSymbol;
    }

    /** True when a swap of {@code inputAddress} into the pool is a zero-for-one swap. */
    public boolean zeroForOne(String inputAddress) {
      String normalized = Addresses.normalize(inputAddress);
      if (normalized.equals(currency0)) {
        return true;
      }
      if (normalized.equals(currency1)) {
        return false;
      }
      throw new IllegalArgumentException(
          "address " + inputAddress + " is not a currency of pool " + pairKey());
    }

    /** The v4 {@code PoolKey} ABI encoding, five words. */
    public byte[] encodedPoolKey() {
      return UniswapAbiEncoder.encodePoolKey(currency0, currency1, fee, tickSpacing, hooks);
    }

    /** The v4 pool id {@code keccak256(abi.encode(PoolKey))} as hex. */
    public String poolId() {
      return UniswapAbiEncoder.poolId(encodedPoolKey());
    }

    /**
     * Validates the pool against the token registry and derives the base/quote currency addresses.
     *
     * <p>A wrapped base (an ETH pair backed by a WETH pool) is resolved by elimination: the side
     * whose token symbol matches the quote symbol fixes the quote currency, and the base is the
     * other currency. Anything ambiguous fails closed.
     */
    public static PoolDefinition create(
        String baseSymbol,
        String quoteSymbol,
        String currency0,
        String currency1,
        int fee,
        int tickSpacing,
        String hooks,
        TokenRegistry tokens) {
      String base = baseSymbol.toUpperCase();
      String quote = quoteSymbol.toUpperCase();
      String c0 = Addresses.requireValidAddress(currency0);
      String c1 = Addresses.requireValidAddress(currency1);
      String hook = Addresses.requireValidAddress(hooks);
      PoolDefinition pool =
          new PoolDefinition(base, quote, c0, c1, fee, tickSpacing, hook, null, null);
      pool.validateBasic(tokens);
      String baseAddress = pool.resolveSide(base, tokens);
      String quoteAddress = pool.resolveSide(quote, tokens);
      if (baseAddress == null && quoteAddress != null) {
        baseAddress = pool.otherCurrency(quoteAddress);
      }
      if (quoteAddress == null && baseAddress != null) {
        quoteAddress = pool.otherCurrency(baseAddress);
      }
      if (baseAddress == null || quoteAddress == null || baseAddress.equals(quoteAddress)) {
        throw new IllegalArgumentException(
            "pool "
                + pool.pairKey()
                + " cannot map its pair sides to the pool currencies "
                + c0
                + "/"
                + c1
                + "; add a token whose symbol matches one side");
      }
      return new PoolDefinition(base, quote, c0, c1, fee, tickSpacing, hook, baseAddress, quoteAddress);
    }

    private void validateBasic(TokenRegistry tokens) {
      if (currency0.equals(currency1)) {
        throw new IllegalArgumentException("pool " + pairKey() + " has identical currencies");
      }
      if (Addresses.value(currency0).compareTo(Addresses.value(currency1)) >= 0) {
        throw new IllegalArgumentException(
            "pool " + pairKey() + " currencies are not sorted: currency0 must be the lower address");
      }
      if (fee < 0 || (fee > MAX_FEE && fee != DYNAMIC_FEE_FLAG)) {
        throw new IllegalArgumentException("pool " + pairKey() + " has invalid fee " + fee);
      }
      if (tickSpacing <= 0 || tickSpacing > MAX_TICK_SPACING) {
        throw new IllegalArgumentException(
            "pool " + pairKey() + " has invalid tickSpacing " + tickSpacing);
      }
      if (tokens.bySymbol(baseSymbol) == null) {
        throw new IllegalArgumentException(
            "pool " + pairKey() + " references unconfigured base token " + baseSymbol);
      }
      if (tokens.bySymbol(quoteSymbol) == null) {
        throw new IllegalArgumentException(
            "pool " + pairKey() + " references unconfigured quote token " + quoteSymbol);
      }
    }

    /** The pool currency whose token symbol matches {@code symbol}, or null. */
    private String resolveSide(String symbol, TokenRegistry tokens) {
      TokenRegistry.Token token0 = tokens.byAddress(currency0);
      TokenRegistry.Token token1 = tokens.byAddress(currency1);
      if (token0 != null && symbol.equals(token0.symbol())) {
        return currency0;
      }
      if (token1 != null && symbol.equals(token1.symbol())) {
        return currency1;
      }
      return null;
    }

    private String otherCurrency(String address) {
      return address.equals(currency0) ? currency1 : currency0;
    }
  }

  private final Map<String, PoolDefinition> byPair;

  private PoolKeyRegistry(Map<String, PoolDefinition> byPair) {
    this.byPair = Collections.unmodifiableMap(new LinkedHashMap<>(byPair));
  }

  /** Builds a registry from an ordered collection of already-validated pools. */
  public static PoolKeyRegistry of(Iterable<PoolDefinition> pools, TokenRegistry tokens) {
    Map<String, PoolDefinition> byPair = new LinkedHashMap<>();
    for (PoolDefinition pool : pools) {
      PoolDefinition previous = byPair.putIfAbsent(pool.pairKey(), pool);
      if (previous != null) {
        throw new IllegalArgumentException("duplicate pool " + pool.pairKey());
      }
    }
    return new PoolKeyRegistry(byPair);
  }

  /** The configured pool for a pair, or {@code null} when the pair is not configured. */
  public PoolDefinition byPair(CurrencyPair pair) {
    return pair == null
        ? null
        : byPair.get(
            pair.getBase().getCurrencyCode() + "/" + pair.getCounter().getCurrencyCode());
  }

  /** All configured pools in configuration order. */
  public java.util.Collection<PoolDefinition> all() {
    return byPair.values();
  }

  /** The sorted-currency helpers for pool validation (exposed for tests). */
  static BigInteger addressValue(String address) {
    return Addresses.value(address);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PoolKeyRegistry)) {
      return false;
    }
    PoolKeyRegistry that = (PoolKeyRegistry) o;
    return byPair.equals(that.byPair);
  }

  @Override
  public int hashCode() {
    return Objects.hash(byPair);
  }

  @Override
  public String toString() {
    return "PoolKeyRegistry" + byPair.keySet();
  }
}
