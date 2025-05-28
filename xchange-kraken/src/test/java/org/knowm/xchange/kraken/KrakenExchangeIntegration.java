package org.knowm.xchange.kraken;

import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class KrakenExchangeIntegration extends KrakenIntegrationTestParent {

    @Test
    public void valid_metadata() {
        assertThat(exchange.getExchangeMetaData()).isNotNull();
        Map<Instrument, InstrumentMetaData> instruments =
                exchange.getExchangeMetaData().getInstruments();
        assertThat(instruments).containsKey(CurrencyPair.BTC_USDT);
    }

}
