package org.knowm.xchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.utils.ObjectMapperHelper;

class CurrencyTest {

  @Test
  void currencyCode() {
    assertThat(Currency.CNY.getCodeCurrency("CNY")).isEqualTo(Currency.CNY);
    assertThat(Currency.CNY.getCodeCurrency("cny")).isEqualTo(Currency.CNY);
  }

  @Test
  void getInstance() {
    assertThat(Currency.getInstance("BTC")).isEqualTo(Currency.BTC);
    assertThat(Currency.getInstance("btc")).isEqualTo(Currency.BTC);
    assertThat(Currency.getInstance("BTC")).isEqualTo(new Currency("btc"));
  }

  @Test
  void testGetInstanceIsCaseInsensitiveAndCached() {
    assertThat(Currency.getInstance("BTC")).isSameAs(Currency.BTC);
    assertThat(Currency.getInstance("btc")).isSameAs(Currency.BTC);
    assertThat(Currency.getInstance("Btc")).isSameAs(Currency.BTC);
    assertThat(Currency.getInstance("XBT")).isSameAs(Currency.XBT);
    assertThat(Currency.getInstance("xbt")).isSameAs(Currency.XBT);
  }

  @Test
  void testGetInstanceCreatesUnknownCodeOnceUppercased() {
    Set<String> existingCodes = Currency.getAvailableCurrencyCodes();
    int suffix = 0;
    while (existingCodes.contains("FASTPATHUNKNOWN" + suffix)) {
      suffix++;
    }
    String lower = "fastpathunknown" + suffix;
    String upper = "FASTPATHUNKNOWN" + suffix;
    String mixed = "FastPathUnknown" + suffix;
    assertThat(Currency.getInstanceNoCreate(lower)).isNull();

    Currency created = Currency.getInstance(lower);

    assertThat(created.getCurrencyCode()).isEqualTo(upper);
    assertThat(Currency.getInstance(upper)).isSameAs(created);
    assertThat(Currency.getInstance(mixed)).isSameAs(created);
    assertThat(Currency.getInstanceNoCreate(lower)).isSameAs(created);
  }

  @Test
  void testGetInstanceRejectsNull() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> Currency.getInstance(null));
  }

  @Test
  void testGetInstanceNoCreateReturnsNullForUnknownCode() {
    Set<String> existingCodes = Currency.getAvailableCurrencyCodes();
    int suffix = 0;
    while (existingCodes.contains("NOSUCHCURRENCYCODE" + suffix)) {
      suffix++;
    }
    assertThat(Currency.getInstanceNoCreate("nosuchcurrencycode" + suffix)).isNull();
    assertThat(Currency.getInstanceNoCreate("NOSUCHCURRENCYCODE" + suffix)).isNull();
  }

  @Test
  void getInstanceNoCreate() {
    assertThat(Currency.getInstanceNoCreate("CNY")).isEqualTo(Currency.CNY);
    assertThat(Currency.getInstanceNoCreate("cny")).isEqualTo(Currency.CNY);
    assertThat(Currency.getInstanceNoCreate("CNY")).isEqualTo(new Currency("cny"));
  }

  @Test
  void equals() {
    assertThat(Currency.XBT).isEqualTo(Currency.BTC);
    assertThat(Currency.XBT).isNotEqualTo(Currency.LTC);

    Currency btc = SerializationUtils.deserialize(SerializationUtils.serialize(Currency.BTC));
    assertThat(btc).isEqualTo(Currency.BTC);
    assertThat(btc).isEqualTo(Currency.XBT);
    assertThat(btc).isNotEqualTo(Currency.LTC);
  }

  @Test
  void testToString() {
    assertThat(Currency.XBT.toString()).isEqualTo("XBT");
    assertThat(Currency.BTC.toString()).isEqualTo("BTC");
  }

  @Test
  void serializeDeserialize() throws Exception {
    Currency jsonCopy = ObjectMapperHelper.viaJSON(Currency.XBT);
    assertThat(jsonCopy).isEqualTo(Currency.XBT);
  }
}
