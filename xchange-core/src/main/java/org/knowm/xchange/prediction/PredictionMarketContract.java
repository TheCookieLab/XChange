package org.knowm.xchange.prediction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Objects;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.instrument.Instrument;

/**
 * Instrument identity for a prediction-market outcome contract.
 *
 * <p>Prediction markets trade event outcomes (for example "Candidate X wins" or "BTC above $90k on
 * Dec 31") rather than base/counter spot pairs or derivatives on base assets. This type gives those
 * outcomes a first-class {@link Instrument} identity so they are never modeled as fake {@link
 * org.knowm.xchange.currency.CurrencyPair} instances.
 *
 * <p>The canonical wire form is slash-delimited and always starts with the literal {@link
 * #WIRE_PREFIX} segment:
 *
 * <pre>
 *   PRED/&lt;provider&gt;/&lt;marketId&gt;/&lt;outcomeId&gt;/&lt;quoteCurrency&gt;
 *   PRED/&lt;provider&gt;/&lt;eventId&gt;/&lt;marketId&gt;/&lt;outcomeId&gt;/&lt;quoteCurrency&gt;
 * </pre>
 *
 * <p>The second form carries the provider-native event identifier when one is known. The explicit
 * prefix keeps the format unambiguous against the slash-count conventions used by {@code
 * CurrencyPair} (1 slash), {@code FuturesContract} (2 slashes), and {@code OptionsContract} (4
 * slashes).
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD}
 *   <li>{@code PRED/polymarket/0xdd22472e/713210456792522125/USD}
 * </ul>
 *
 * <p>Identity (equality, hash code, and ordering) covers the provider, optional event id, market
 * id, outcome id, and quote currency. Provider display symbols, settlement state, and event
 * grouping metadata belong in provider-specific metadata companions and raw DTOs, not on this
 * type.
 */
public class PredictionMarketContract extends Instrument
    implements Comparable<PredictionMarketContract>, Serializable {

  private static final long serialVersionUID = 2627341021477339247L;

  /** Canonical wire prefix that unambiguously identifies a prediction-market instrument string. */
  public static final String WIRE_PREFIX = "PRED";

  private static final Comparator<PredictionMarketContract> COMPARATOR =
      Comparator.comparing(PredictionMarketContract::getProvider)
          .thenComparing(
              PredictionMarketContract::getEventId,
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(PredictionMarketContract::getMarketId)
          .thenComparing(PredictionMarketContract::getOutcomeId)
          .thenComparing(PredictionMarketContract::getQuoteCurrency);

  /** Provider namespace that owns the native identifiers, for example {@code kalshi}. */
  private final String provider;

  /** Provider-native event identifier, when the provider groups markets under events. */
  private final String eventId;

  /** Provider-native market identifier. */
  private final String marketId;

  /** Provider-native outcome identifier or name (for example {@code YES} or an outcome token id). */
  private final String outcomeId;

  /** Currency the outcome is quoted and settled in, typically a fiat currency such as USD. */
  private final Currency quoteCurrency;

  /**
   * Creates a contract without an event identifier.
   *
   * @param provider provider namespace owning the native ids, for example {@code kalshi}
   * @param marketId provider-native market identifier
   * @param outcomeId provider-native outcome identifier or name
   * @param quoteCurrency currency the outcome is quoted and settled in
   */
  public PredictionMarketContract(
      String provider, String marketId, String outcomeId, Currency quoteCurrency) {
    this(provider, null, marketId, outcomeId, quoteCurrency);
  }

  /**
   * Creates a contract with an event identifier.
   *
   * @param provider provider namespace owning the native ids, for example {@code kalshi}
   * @param eventId provider-native event identifier, or {@code null} when not applicable
   * @param marketId provider-native market identifier
   * @param outcomeId provider-native outcome identifier or name
   * @param quoteCurrency currency the outcome is quoted and settled in
   */
  public PredictionMarketContract(
      String provider, String eventId, String marketId, String outcomeId, Currency quoteCurrency) {
    this.provider = requireSegment(provider, "provider");
    this.eventId = eventId == null ? null : requireSegment(eventId, "eventId");
    this.marketId = requireSegment(marketId, "marketId");
    this.outcomeId = requireSegment(outcomeId, "outcomeId");
    this.quoteCurrency = Objects.requireNonNull(quoteCurrency, "quoteCurrency");
  }

  /**
   * Parses the canonical wire form, with or without an event identifier segment.
   *
   * @param symbol wire string such as {@code PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD}
   * @throws IllegalArgumentException when the string is not a valid prediction-market instrument
   */
  @JsonCreator
  public PredictionMarketContract(final String symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("Could not parse prediction market contract from 'null'");
    }

    /*
     * Performance optimization: Scanning slash delimiters using indexOf() avoids intermediate
     * ArrayList buffer creation and String[] array allocation inherent to String.split(),
     * reducing heap allocation overhead and latency during high-frequency Jackson deserialization
     * and streaming market data processing.
     */
    int s1 = symbol.indexOf('/');
    if (s1 != WIRE_PREFIX.length() || !symbol.startsWith(WIRE_PREFIX)) {
      throw new IllegalArgumentException(
          "Could not parse prediction market contract from '" + symbol + "'");
    }
    int s2 = symbol.indexOf('/', s1 + 1);
    int s3 = s2 >= 0 ? symbol.indexOf('/', s2 + 1) : -1;
    int s4 = s3 >= 0 ? symbol.indexOf('/', s3 + 1) : -1;
    if (s2 < 0 || s3 < 0 || s4 < 0) {
      throw new IllegalArgumentException(
          "Could not parse prediction market contract from '" + symbol + "'");
    }
    int s5 = symbol.indexOf('/', s4 + 1);
    int s6 = s5 >= 0 ? symbol.indexOf('/', s5 + 1) : -1;

    if (s5 < 0) {
      // 5 segments (4 slashes): PRED/<provider>/<marketId>/<outcomeId>/<quoteCurrency>
      this.provider = requireSegment(symbol.substring(s1 + 1, s2), "provider");
      this.eventId = null;
      this.marketId = requireSegment(symbol.substring(s2 + 1, s3), "marketId");
      this.outcomeId = requireSegment(symbol.substring(s3 + 1, s4), "outcomeId");
      this.quoteCurrency = Currency.getInstance(requireSegment(symbol.substring(s4 + 1), "quoteCurrency"));
    } else if (s6 < 0) {
      // 6 segments (5 slashes): PRED/<provider>/<eventId>/<marketId>/<outcomeId>/<quoteCurrency>
      this.provider = requireSegment(symbol.substring(s1 + 1, s2), "provider");
      this.eventId = requireSegment(symbol.substring(s2 + 1, s3), "eventId");
      this.marketId = requireSegment(symbol.substring(s3 + 1, s4), "marketId");
      this.outcomeId = requireSegment(symbol.substring(s4 + 1, s5), "outcomeId");
      this.quoteCurrency = Currency.getInstance(requireSegment(symbol.substring(s5 + 1), "quoteCurrency"));
    } else {
      throw new IllegalArgumentException(
          "Could not parse prediction market contract from '" + symbol + "'");
    }
  }

  /**
   * Returns whether the given instrument wire string names a prediction-market contract, as
   * opposed to a currency pair, futures contract, or options contract wire string.
   *
   * @param instrumentString instrument wire string, may be {@code null}
   * @return {@code true} when the string starts with the prediction-market wire prefix
   */
  public static boolean isWireString(String instrumentString) {
    return instrumentString != null && instrumentString.startsWith(WIRE_PREFIX + "/");
  }

  private static String requireSegment(String value, String name) {
    if (value == null || value.isBlank() || value.contains("/")) {
      throw new IllegalArgumentException(
          "Prediction market contract segment '" + name + "' must be non-blank and slash-free");
    }
    return value;
  }

  /**
   * @return provider namespace owning the native ids, for example {@code kalshi}
   */
  public String getProvider() {
    return provider;
  }

  /**
   * @return provider-native event identifier, or {@code null} when not applicable
   */
  public String getEventId() {
    return eventId;
  }

  /**
   * @return provider-native market identifier
   */
  public String getMarketId() {
    return marketId;
  }

  /**
   * @return provider-native outcome identifier or name
   */
  public String getOutcomeId() {
    return outcomeId;
  }

  /**
   * @return currency the outcome is quoted and settled in
   */
  public Currency getQuoteCurrency() {
    return quoteCurrency;
  }

  /**
   * Prediction outcomes have no base currency; the traded unit is an outcome share, not a currency
   * amount.
   *
   * <p>This contractually mirrors the {@link Instrument#getBase()} abstraction, which is explicitly
   * nullable for instruments whose traded unit is not a currency amount. Generic consumers must
   * check for {@code null} instead of assuming a non-null base.
   *
   * @return {@code null} always
   */
  @Override
  @Nullable
  public Currency getBase() {
    return null;
  }

  /**
   * @return the quote currency, same as {@link #getQuoteCurrency()}
   */
  @Override
  public Currency getCounter() {
    return quoteCurrency;
  }

  @Override
  public int compareTo(final PredictionMarketContract that) {
    return COMPARATOR.compare(this, that);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PredictionMarketContract that = (PredictionMarketContract) o;
    return Objects.equals(provider, that.provider)
        && Objects.equals(eventId, that.eventId)
        && Objects.equals(marketId, that.marketId)
        && Objects.equals(outcomeId, that.outcomeId)
        && Objects.equals(quoteCurrency, that.quoteCurrency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provider, eventId, marketId, outcomeId, quoteCurrency);
  }

  @JsonValue
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(WIRE_PREFIX).append('/').append(provider).append('/');
    if (eventId != null) {
      sb.append(eventId).append('/');
    }
    return sb.append(marketId)
        .append('/')
        .append(outcomeId)
        .append('/')
        .append(quoteCurrency.getCurrencyCode())
        .toString();
  }
}
