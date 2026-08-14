package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;

/** Verifies the {@code instType} mapping used for order-history queries. */
public class OkxTradeServiceTest {

  @Test
  public void historyInstrumentTypeMapsPerInstrumentFamily() {
    assertThat(OkxTradeService.historyInstrumentType(new CurrencyPair("BTC/USDT")))
        .isEqualTo("SPOT");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo("SWAP");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USD/SWAP")))
        .isEqualTo("SWAP");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USDT/260814")))
        .isEqualTo("FUTURES");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USD/260814")))
        .isEqualTo("FUTURES");
    assertThat(
            OkxTradeService.historyInstrumentType(new OptionsContract("BTC/USD/260828/110000/C")))
        .isEqualTo("OPTION");
  }
}
