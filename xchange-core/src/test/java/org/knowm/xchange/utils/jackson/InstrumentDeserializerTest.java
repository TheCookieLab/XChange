package org.knowm.xchange.utils.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.prediction.PredictionMarketContract;
import org.knowm.xchange.utils.ObjectMapperHelper;

/** Tests instrument string dispatch for {@link InstrumentDeserializer} and {@link InstrumentMapDeserializer}. */
public class InstrumentDeserializerTest {

  @Test
  public void testValueDispatch() throws IOException {
    assertThat(ObjectMapperHelper.readValue("\"BTC/USD\"", Instrument.class))
        .isInstanceOf(CurrencyPair.class);
    assertThat(ObjectMapperHelper.readValue("\"BTC/USD/200925\"", Instrument.class))
        .isInstanceOf(FuturesContract.class);
    // Options keep their 4-slash form; the PRED prefix must not hijack them.
    assertThat(ObjectMapperHelper.readValue("\"BTC/USD/200925/8956.67/P\"", Instrument.class))
        .isInstanceOf(OptionsContract.class);
    assertThat(
            ObjectMapperHelper.readValue(
                "\"PRED/polymarket/0xdd22472e/713210456792522125/USD\"", Instrument.class))
        .isInstanceOf(PredictionMarketContract.class);
    assertThat(
            ObjectMapperHelper.readValue(
                "\"PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD\"", Instrument.class))
        .isInstanceOf(PredictionMarketContract.class);
  }

  @Test
  public void testValueRoundTrip() throws IOException {
    Instrument contract =
        new PredictionMarketContract("PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD");
    String json = ObjectMapperHelper.toCompactJSON(contract);
    assertThat(json)
        .isEqualTo("\"PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD\"");
    assertThat(ObjectMapperHelper.readValue(json, Instrument.class)).isEqualTo(contract);
  }

  @Test
  public void testKeyDispatch() {
    InstrumentMapDeserializer keyDeserializer = new InstrumentMapDeserializer();

    assertThat(keyDeserializer.deserializeKey("BTC/USD", null)).isInstanceOf(CurrencyPair.class);
    assertThat(keyDeserializer.deserializeKey("BTC/USD/200925", null))
        .isInstanceOf(FuturesContract.class);
    assertThat(keyDeserializer.deserializeKey("BTC/USD/200925/8956.67/P", null))
        .isInstanceOf(OptionsContract.class);
    assertThat(
            keyDeserializer.deserializeKey(
                "PRED/polymarket/0xdd22472e/713210456792522125/USD", null))
        .isInstanceOf(PredictionMarketContract.class);
    assertThat(
            keyDeserializer.deserializeKey(
                "PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD", null))
        .isInstanceOf(PredictionMarketContract.class);
  }
}
