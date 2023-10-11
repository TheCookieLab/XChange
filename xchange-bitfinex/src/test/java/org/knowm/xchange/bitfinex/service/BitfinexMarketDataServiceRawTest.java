package org.knowm.xchange.bitfinex.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitfinex.BitfinexExchangeWiremock;
import org.knowm.xchange.bitfinex.v2.dto.marketdata.BitfinexCurrencyChain;
import org.knowm.xchange.currency.Currency;

class BitfinexMarketDataServiceRawTest extends BitfinexExchangeWiremock {

  BitfinexMarketDataServiceRaw bitfinexMarketDataServiceRaw = (BitfinexMarketDataServiceRaw) exchange.getMarketDataService();


  @Test
  void allChains() throws IOException {
    List<BitfinexCurrencyChain> expected = Arrays.asList(
        BitfinexCurrencyChain.builder()
            .currency(new Currency("1INCH"))
            .chainName("ETH")
            .build(),
        BitfinexCurrencyChain.builder()
            .currency(new Currency("ATLAS"))
            .chainName("SOL")
            .build()
    );
    List<BitfinexCurrencyChain> actual = bitfinexMarketDataServiceRaw.allChains();

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

}