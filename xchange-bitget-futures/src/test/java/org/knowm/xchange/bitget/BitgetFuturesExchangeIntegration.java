package org.knowm.xchange.bitget;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BitgetFuturesExchangeIntegration extends BitgetFuturesIntegrationTestParent {

    @Test
    void valid_metadata() {
        assertThat(exchange.getExchangeMetaData()).isNotNull();
        Map<Instrument, InstrumentMetaData> instruments = exchange.getExchangeMetaData().getInstruments();
        assertThat(instruments).containsKey(new FuturesContract(CurrencyPair.BTC_USDT, "PERP"));
    }


}
