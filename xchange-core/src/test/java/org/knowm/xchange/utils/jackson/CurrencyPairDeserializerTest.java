package org.knowm.xchange.utils.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.utils.ObjectMapperHelper;

public class CurrencyPairDeserializerTest {

  public static class PairContainer {
    @JsonDeserialize(using = CurrencyPairDeserializer.class)
    public CurrencyPair pair;

    public String after;
  }

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

  @Test
  public void testCaseExpansionAndSplitCompatibility() {
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("\u00df/USD"))
        .isEqualTo(new CurrencyPair("SS", "USD"));
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("\u00dfaA/BTC\n"))
        .isEqualTo(new CurrencyPair("SSAA", "BTC"));
    assertThat(CurrencyPairDeserializer.getCurrencyPairFromString("BTC/USD///"))
        .isEqualTo(CurrencyPair.BTC_USD);
    CurrencyPair leadingSeparator = CurrencyPairDeserializer.getCurrencyPairFromString("/BTC");
    assertThat(leadingSeparator.getBase().getCurrencyCode()).isEmpty();
    assertThat(leadingSeparator.getCounter().getCurrencyCode()).isEqualTo("BTC");
  }

  @Test
  public void testJacksonNonStringTokensConsumeTheWholeValue() throws IOException {
    for (String value : new String[] {"{\"base\":\"BTC\"}", "[\"BTC/USD\"]"}) {
      PairContainer result =
          ObjectMapperHelper.readValueStrict(
              "{\"pair\":" + value + ",\"after\":\"kept\"}", PairContainer.class);
      assertThat(result.pair).isNull();
      assertThat(result.after).isEqualTo("kept");
    }

    PairContainer number =
        ObjectMapperHelper.readValueStrict(
            "{\"pair\":1e2,\"after\":\"kept\"}", PairContainer.class);
    assertThat(number.pair).isEqualTo(new CurrencyPair("100", "0"));
    assertThat(number.after).isEqualTo("kept");
  }
}
