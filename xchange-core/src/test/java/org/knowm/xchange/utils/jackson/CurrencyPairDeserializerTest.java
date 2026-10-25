package org.knowm.xchange.utils.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.utils.ObjectMapperHelper;

public class CurrencyPairDeserializerTest {

  @Test
  public void testNullAndEmpty() {
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString(null)).isNull();
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("")).isNull();
  }

  @Test
  public void testStandardSeparators() {
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("BTC/USD"))
        .isEqualTo(CurrencyPair.BTC_USD);
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("BTC_USD"))
        .isEqualTo(CurrencyPair.BTC_USD);
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("BTC-USD"))
        .isEqualTo(CurrencyPair.BTC_USD);
  }

  @Test
  public void testLowerCaseAndMixedCase() {
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("btc/usd"))
        .isEqualTo(CurrencyPair.BTC_USD);

    CurrencyPair mixed = CurrencyPairDeserializer.getCurrencyPairFromString("Btc/usd");
    assertThat(mixed.getBase().getCurrencyCode()).isEqualTo("BTC");
    assertThat(mixed.getCounter().getCurrencyCode()).isEqualTo("USD");
  }

  @Test
  public void testUnseparatedSymbols() {
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("BTCUSD"))
        .isEqualTo(CurrencyPair.BTC_USD);
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("DOGEBTC"))
        .isEqualTo(CurrencyPair.DOGE_BTC);
  }

  @Test
  public void testJacksonDeserialization() throws IOException {
    CurrencyPair pair = ObjectMapperHelper.readValue("\"BTC/USD\"", CurrencyPair.class);
    assertThat(pair).isEqualTo(CurrencyPair.BTC_USD);
  }
}
