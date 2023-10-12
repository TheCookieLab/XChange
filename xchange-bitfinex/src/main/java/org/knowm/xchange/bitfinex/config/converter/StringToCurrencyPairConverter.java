package org.knowm.xchange.bitfinex.config.converter;

import com.fasterxml.jackson.databind.util.StdConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.currency.CurrencyPair;

/**
 * Converts string value {@code Currency}
 */
public class StringToCurrencyPairConverter extends StdConverter<String, CurrencyPair> {

  @Override
  public CurrencyPair convert(String value) {
    if (value.contains(":")) {
      return new CurrencyPair(StringUtils.replaceOnce(value, ":", "/"));
    }
    Validate.isTrue(value.length() == 6);
    return new CurrencyPair(value.substring(0, 3), value.substring(3));
  }

}