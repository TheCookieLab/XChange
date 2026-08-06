package org.knowm.xchange.instrument;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import javax.annotation.Nullable;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.utils.jackson.InstrumentDeserializer;

/**
 * Base object for financial instruments supported in xchange such as CurrencyPair, Future or Option
 */
@JsonDeserialize(using = InstrumentDeserializer.class)
public abstract class Instrument implements Serializable {

  private static final long serialVersionUID = 414711266389792746L;

  /**
   * Base currency of the instrument.
   *
   * <p>Concrete instruments such as {@link org.knowm.xchange.currency.CurrencyPair} always expose a
   * base currency. Instruments whose traded unit is not a currency amount — for example
   * prediction-market outcome shares — return {@code null} here; generic consumers MUST check for
   * {@code null} before dereferencing instead of assuming a non-null base.
   *
   * @return base currency, or {@code null} when the instrument has no base currency
   */
  @Nullable
  public abstract Currency getBase();

  public abstract Currency getCounter();
}
