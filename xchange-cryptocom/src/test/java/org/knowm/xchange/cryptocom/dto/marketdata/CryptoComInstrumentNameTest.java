package org.knowm.xchange.cryptocom.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class CryptoComInstrumentNameTest {

  @Test
  public void spotPair() {
    CryptoComInstrumentIdentity identity = CryptoComInstrumentIdentity.parse("BTC_USD");
    assertThat(identity).isNotNull();
    assertThat(identity.getProductType()).isEqualTo(CryptoComInstrumentIdentity.ProductType.SPOT);
    assertThat(identity.getBaseCurrency()).isEqualTo("BTC");
    assertThat(identity.getQuoteCurrency()).isEqualTo("USD");
    assertThat(identity.getExpiry()).isNull();
    assertThat(identity.isDerivative()).isFalse();
  }

  @Test
  public void derivativeSuffixesQuoteUsd() {
    assertThat(CryptoComInstrumentIdentity.parse("ETHUSD-PERP").getBaseCurrency()).isEqualTo("ETH");
    assertThat(CryptoComInstrumentIdentity.parse("BTCUSD-250627").getBaseCurrency()).isEqualTo("BTC");
    assertThat(CryptoComInstrumentIdentity.parse("SOLUSD-251219-150-C").getBaseCurrency())
        .isEqualTo("SOL");
  }

  @Test
  public void perpetual() {
    CryptoComInstrumentIdentity identity = CryptoComInstrumentIdentity.parse("BTCUSD-PERP");
    assertThat(identity.getProductType())
        .isEqualTo(CryptoComInstrumentIdentity.ProductType.PERPETUAL_SWAP);
    assertThat(identity.getQuoteCurrency()).isEqualTo("USD");
    assertThat(identity.getExpiry()).isNull();
    assertThat(identity.isDerivative()).isTrue();
  }

  @Test
  public void future() {
    CryptoComInstrumentIdentity identity = CryptoComInstrumentIdentity.parse("BTCUSD-250627");
    assertThat(identity.getProductType()).isEqualTo(CryptoComInstrumentIdentity.ProductType.FUTURE);
    assertThat(identity.getExpiry()).isEqualTo("250627");
    assertThat(identity.getStrikePrice()).isNull();
    assertThat(identity.isDerivative()).isTrue();
  }

  @Test
  public void callOption() {
    CryptoComInstrumentIdentity identity = CryptoComInstrumentIdentity.parse("BTCUSD-250627-60000-C");
    assertThat(identity.getProductType()).isEqualTo(CryptoComInstrumentIdentity.ProductType.OPTION);
    assertThat(identity.getExpiry()).isEqualTo("250627");
    assertThat(identity.getStrikePrice()).isEqualTo("60000");
    assertThat(identity.getOptionSide()).isEqualTo('C');
    assertThat(identity.isOption()).isTrue();
    assertThat(identity.isDerivative()).isTrue();
  }

  @Test
  public void putOption() {
    CryptoComInstrumentIdentity identity = CryptoComInstrumentIdentity.parse("BTCUSD-250627-60000-P");
    assertThat(identity.getProductType()).isEqualTo(CryptoComInstrumentIdentity.ProductType.OPTION);
    assertThat(identity.getOptionSide()).isEqualTo('P');
  }

  @Test
  public void fractionalStrikeOption() {
    CryptoComInstrumentIdentity identity =
        CryptoComInstrumentIdentity.parse("ETHUSD-250627-3450.5-C");
    assertThat(identity.getProductType()).isEqualTo(CryptoComInstrumentIdentity.ProductType.OPTION);
    assertThat(identity.getStrikePrice()).isEqualTo("3450.5");
  }

  @Test
  public void rejectsUnknownShapes() {
    assertThat(CryptoComInstrumentIdentity.parse(null)).isNull();
    assertThat(CryptoComInstrumentIdentity.parse("")).isNull();
    assertThat(CryptoComInstrumentIdentity.parse("BTCUSD")).isNull();
    assertThat(CryptoComInstrumentIdentity.parse("BTC-USD")).isNull();
    assertThat(CryptoComInstrumentIdentity.parse("BTCUSD-250627-60000")).isNull();
    assertThat(CryptoComInstrumentIdentity.parse("BTCUSD-250627-60000-X")).isNull();
    assertThat(CryptoComInstrumentIdentity.parse("BTCUSD-PERP-EXTRA")).isNull();
  }
}