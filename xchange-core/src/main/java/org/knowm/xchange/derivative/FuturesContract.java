package org.knowm.xchange.derivative;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;

public class FuturesContract extends Instrument
    implements Derivative, Comparable<FuturesContract>, Serializable {

  private static final long serialVersionUID = 6876906648149216819L;

  // Precompiled pattern to avoid repeated regex compilation on every isPerpetual call
  private static final Pattern PERPETUAL_PATTERN = Pattern.compile("(?i)PERP|SWAP|PERPETUAL");

  private static final Comparator<FuturesContract> COMPARATOR =
      Comparator.comparing(FuturesContract::getCurrencyPair)
          .thenComparing(FuturesContract::getPrompt);

  /** The CurrencyPair the FuturesContract is based upon */
  private final CurrencyPair currencyPair;

  /** The Date when the FuturesContract expires, when null it is perpetual */
  private final String prompt;

  public FuturesContract(CurrencyPair currencyPair, String prompt) {
    this.currencyPair = currencyPair;
    this.prompt = prompt;
  }

  @JsonCreator
  public FuturesContract(final String symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("Could not parse futures contract from 'null'");
    }
    int firstSlash = symbol.indexOf('/');
    int secondSlash = firstSlash >= 0 ? symbol.indexOf('/', firstSlash + 1) : -1;
    if (firstSlash <= 0 || secondSlash <= firstSlash + 1 || secondSlash == symbol.length() - 1) {
      throw new IllegalArgumentException("Could not parse futures contract from '" + symbol + "'");
    }

    String base = symbol.substring(0, firstSlash);
    String counter = symbol.substring(firstSlash + 1, secondSlash);

    int thirdSlash = symbol.indexOf('/', secondSlash + 1);
    String prompt =
        thirdSlash < 0
            ? symbol.substring(secondSlash + 1)
            : symbol.substring(secondSlash + 1, thirdSlash);

    this.currencyPair = new CurrencyPair(base, counter);
    this.prompt = prompt;
  }

  @Override
  public CurrencyPair getCurrencyPair() {
    return currencyPair;
  }

  public String getPrompt() {
    return prompt;
  }

  public boolean isPerpetual() {
    return this.prompt != null && PERPETUAL_PATTERN.matcher(this.prompt).matches();
  }

  @Override
  public int compareTo(final FuturesContract that) {
    return COMPARATOR.compare(this, that);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final FuturesContract contract = (FuturesContract) o;
    return Objects.equals(currencyPair, contract.currencyPair)
        && Objects.equals(prompt, contract.prompt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currencyPair, prompt);
  }

  @Override
  public Currency getBase() {
    return currencyPair.getBase();
  }

  @Override
  public Currency getCounter() {
    return currencyPair.getCounter();
  }

  @JsonValue
  @Override
  public String toString() {

    return currencyPair + "/" + prompt;
  }
}
