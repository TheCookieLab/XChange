package org.knowm.xchange.binance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;

public class BinanceConfigurationTest {

  private ExchangeSpecification spec() {
    return new ExchangeSpecification(BinanceExchange.class);
  }

  @Test
  public void testDefaultsToSpotWithProductionUrl() {
    BinanceConfiguration config = BinanceConfiguration.from(spec());

    assertThat(config.getProductFamily()).isEqualTo(BinanceProductFamily.SPOT);
    assertThat(config.getKeyAlgorithm()).isEqualTo(BinanceKeyAlgorithm.HMAC_SHA_256);
    assertThat(config.getTimestampUnit()).isEqualTo(BinanceTimestampUnit.MILLISECONDS);
    assertThat(config.isSandboxEnabled()).isFalse();
    assertThat(config.getRecvWindow()).isNull();
    assertThat(config.getRestBaseUrl()).isEqualTo("https://api.binance.com");
  }

  @Test
  public void testTypedProductFamilySelectsUrl() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(
        BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.USDM);

    BinanceConfiguration config = BinanceConfiguration.from(specification);

    assertThat(config.getProductFamily()).isEqualTo(BinanceProductFamily.USDM);
    assertThat(config.getRestBaseUrl()).isEqualTo("https://fapi.binance.com");
  }

  @Test
  public void testSandboxSelectsSandboxUrl() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);

    BinanceConfiguration config = BinanceConfiguration.from(specification);

    assertThat(config.isSandboxEnabled()).isTrue();
    assertThat(config.getRestBaseUrl()).isEqualTo("https://testnet.binance.vision");
  }

  @Test
  public void testRestBaseUrlOverrideWins() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(
        BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.USDM);
    specification.setExchangeSpecificParametersItem(
        BinanceConfiguration.REST_BASE_URL, "https://example.binance.internal");

    BinanceConfiguration config = BinanceConfiguration.from(specification);

    assertThat(config.getRestBaseUrl()).isEqualTo("https://example.binance.internal");
  }

  @Test
  public void testOptionsFamilyRejectedWithActionableMessage() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(
        BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.OPTIONS);

    assertThatThrownBy(() -> BinanceConfiguration.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("OPTIONS")
        .hasMessageContaining("not implemented");
  }

  @Test
  public void testLegacyExchangeTypeStillHonored() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(
        BinanceExchange.EXCHANGE_TYPE, org.knowm.xchange.binance.dto.ExchangeType.FUTURES);

    BinanceConfiguration config = BinanceConfiguration.from(specification);

    assertThat(config.getProductFamily()).isEqualTo(BinanceProductFamily.USDM);
    assertThat(config.getRestBaseUrl()).isEqualTo("https://fapi.binance.com");
  }

  @Test
  public void testLegacyInverseExchangeTypeStillHonored() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(
        BinanceExchange.EXCHANGE_TYPE, org.knowm.xchange.binance.dto.ExchangeType.INVERSE);

    assertThat(BinanceConfiguration.from(specification).getProductFamily())
        .isEqualTo(BinanceProductFamily.COINM);
  }

  @Test
  public void testInvalidRecvWindowRejected() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(BinanceConfiguration.RECV_WINDOW, 60_001L);

    assertThatThrownBy(() -> BinanceConfiguration.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(BinanceConfiguration.RECV_WINDOW)
        .hasMessageContaining("[0, 60000]");
  }

  @Test
  public void testLegacyRecvWindowStillHonored() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem("recvWindow", 5000);

    assertThat(BinanceConfiguration.from(specification).getRecvWindow()).isEqualTo(5000L);
  }

  @Test
  public void testWrongTypedParameterTypeRejected() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(BinanceConfiguration.PRODUCT_FAMILY, "spot");

    assertThatThrownBy(() -> BinanceConfiguration.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(BinanceConfiguration.PRODUCT_FAMILY);
  }

  @Test
  public void testInvalidOrderBookDepthRejected() {
    ExchangeSpecification specification = spec();
    specification.setExchangeSpecificParametersItem(BinanceConfiguration.ORDER_BOOK_DEPTH, 0);

    assertThatThrownBy(() -> BinanceConfiguration.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive integer");
  }
}
