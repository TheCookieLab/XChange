package org.knowm.xchange.mexc.v3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;

/**
 * Symbol conversion between XChange {@link CurrencyPair}s and MEXC Spot v3 wire symbols.
 *
 * <p>v3 symbols are concatenated uppercase strings (e.g. {@code BTCUSDT}); there is no separator as
 * in the frozen v2 API. {@link #toMexcSymbol(Instrument)} always produces the canonical uppercase
 * concatenation. {@link #toCurrencyPair(String)} resolves the quote by longest-suffix match
 * against the known MEXC quote currencies and throws instead of guessing when nothing matches;
 * exchangeInfo remains the authoritative mapping (see the raw market-data service).
 */
public final class MexcV3Symbols {

  /** Quote currencies commonly seen on MEXC Spot, longest first for suffix disambiguation. */
  private static final List<String> KNOWN_QUOTES =
      Arrays.asList(
          "USDT", "USDC", "USD1", "USDF", "BRL", "BTC", "ETH", "MX", "BNB", "DAI", "TUSD", "EUR",
          "USD");

  private MexcV3Symbols() {}

  /** Converts an instrument to the MEXC v3 wire symbol (uppercase base+quote). */
  public static String toMexcSymbol(Instrument instrument) {
    if (instrument == null) {
      throw new IllegalArgumentException("Instrument must not be null");
    }
    return instrument.getBase().getCurrencyCode().toUpperCase()
        + instrument.getCounter().getCurrencyCode().toUpperCase();
  }

  /**
   * Parses a MEXC v3 wire symbol into a currency pair by longest-suffix quote match.
   *
   * @throws IllegalArgumentException when no known quote currency matches the suffix — callers
   *     must not guess.
   */
  public static CurrencyPair toCurrencyPair(String symbol) {
    if (symbol == null || symbol.isEmpty()) {
      throw new IllegalArgumentException("MEXC symbol must not be empty");
    }
    String upper = symbol.toUpperCase();
    Optional<String> quote =
        KNOWN_QUOTES.stream()
            .filter(q -> upper.length() > q.length() && upper.endsWith(q))
            .max(Comparator.comparingInt(String::length));
    if (!quote.isPresent()) {
      throw new IllegalArgumentException(
          "Cannot resolve MEXC symbol '" + symbol + "' to a currency pair: no known quote suffix");
    }
    String quoteCurrency = quote.get();
    return new CurrencyPair(
        upper.substring(0, upper.length() - quoteCurrency.length()), quoteCurrency);
  }
}
