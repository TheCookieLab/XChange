package org.knowm.xchange.deribit.v2.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.deribit.DeribitIntegrationTestParent;
import org.knowm.xchange.instrument.Instrument;

public class DeribitMarketDataServiceIntegration extends DeribitIntegrationTestParent {

  @Test
  void valid_currencies() throws IOException {
    List<Currency> currencies =
        ((DeribitMarketDataService) exchange.getMarketDataService()).getCurrencies();

    assertThat(currencies).isNotEmpty();
    assertThat(currencies.stream().distinct().count()).isEqualTo(currencies.size());
  }

  @Test
  void valid_instruments() throws IOException {
    List<Instrument> instruments =
        ((DeribitMarketDataService) exchange.getMarketDataService()).getInstruments();

    assertThat(instruments).isNotEmpty();
    assertThat(instruments.stream().distinct().count()).isEqualTo(instruments.size());
  }


}
