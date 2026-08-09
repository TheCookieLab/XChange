package org.knowm.xchange.uniswap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.uniswap.util.Addresses;

/**
 * Immutable registry of the ERC-20 tokens (and native currency) a Uniswap configuration is allowed
 * to trade.
 *
 * <p>The registry is fail-closed: every token must be declared in the configuration before it can
 * be quoted, swapped, or read as a balance. Duplicate symbols or addresses are rejected.
 */
public final class TokenRegistry {

  /** A token that is allowed by configuration. */
  public record Token(String symbol, String address, int decimals, boolean nativeCurrency) {

    public Token {
      symbol = symbol.toUpperCase();
      address = Addresses.requireValidAddress(address);
      if (decimals < 0 || decimals > 36) {
        throw new IllegalArgumentException("token " + symbol + " has unsupported decimals " + decimals);
      }
    }

    /** The human-readable scale factor for {@link #decimals()}. */
    public java.math.BigDecimal scale() {
      return java.math.BigDecimal.TEN.pow(decimals);
    }
  }

  private final Map<String, Token> bySymbol;
  private final Map<String, Token> byAddress;

  private TokenRegistry(Map<String, Token> bySymbol, Map<String, Token> byAddress) {
    this.bySymbol = Collections.unmodifiableMap(new LinkedHashMap<>(bySymbol));
    this.byAddress = Collections.unmodifiableMap(new LinkedHashMap<>(byAddress));
  }

  /** Builds a registry from an ordered collection of tokens, rejecting duplicates. */
  public static TokenRegistry of(Iterable<Token> tokens) {
    Map<String, Token> bySymbol = new ConcurrentHashMap<>();
    Map<String, Token> byAddress = new ConcurrentHashMap<>();
    for (Token token : tokens) {
      Token previous = bySymbol.putIfAbsent(token.symbol(), token);
      if (previous != null) {
        throw new IllegalArgumentException("duplicate token symbol " + token.symbol());
      }
      previous = byAddress.putIfAbsent(token.address(), token);
      if (previous != null) {
        throw new IllegalArgumentException(
            "duplicate token address " + token.address() + " for " + token.symbol());
      }
    }
    return new TokenRegistry(bySymbol, byAddress);
  }

  /** Returns the configured token for a symbol, or {@code null} when not configured. */
  public Token bySymbol(String symbol) {
    return symbol == null ? null : bySymbol.get(symbol.toUpperCase());
  }

  /** Returns the configured token for an address, or {@code null} when not configured. */
  public Token byAddress(String address) {
    return address == null ? null : byAddress.get(Addresses.normalize(address));
  }

  /** All configured tokens in configuration order. */
  public java.util.Collection<Token> all() {
    return bySymbol.values();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TokenRegistry)) {
      return false;
    }
    TokenRegistry that = (TokenRegistry) o;
    return bySymbol.equals(that.bySymbol);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bySymbol);
  }

  @Override
  public String toString() {
    return "TokenRegistry" + bySymbol.keySet();
  }
}
