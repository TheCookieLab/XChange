package org.knowm.xchange.bitfinex;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;

class BitfinexExchangeTest extends BitfinexExchangeWiremock {

  @Test
  void metadata_present() {
    InstrumentMetaData expected = new InstrumentMetaData.Builder()
        .maximumAmount(new BigDecimal("2000.0"))
        .minimumAmount(new BigDecimal("0.00006"))
        .priceScale(0)
        .build();

    Map<Instrument, InstrumentMetaData> instruments = exchange.getExchangeMetaData().getInstruments();
    assertThat(instruments).hasSize(2);

    InstrumentMetaData actual = exchange.getExchangeMetaData().getInstruments().get(CurrencyPair.BTC_USD);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }


}