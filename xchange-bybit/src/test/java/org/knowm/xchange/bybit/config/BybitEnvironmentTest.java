package org.knowm.xchange.bybit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_TESTNET;

import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.BybitCategory;

public class BybitEnvironmentTest {

  private ExchangeSpecification specification() {
    return new BybitExchange().getDefaultExchangeSpecification();
  }

  @Test
  public void resolveDefaultsToProduction() {
    assertThat(BybitEnvironment.resolve(specification())).isEqualTo(BybitEnvironment.PRODUCTION);
  }

  @Test
  public void resolveExplicitFalseFlagsSelectProduction() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, false);
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, false);
    assertThat(BybitEnvironment.resolve(spec)).isEqualTo(BybitEnvironment.PRODUCTION);
  }

  @Test
  public void resolveSandboxFlagSelectsDemo() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    assertThat(BybitEnvironment.resolve(spec)).isEqualTo(BybitEnvironment.DEMO);
  }

  @Test
  public void resolveTestnetFlagSelectsTestnet() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
    assertThat(BybitEnvironment.resolve(spec)).isEqualTo(BybitEnvironment.TESTNET);
  }

  @Test
  public void resolveRejectsConflictingDemoAndTestnetFlags() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
    Throwable thrown = catchThrowable(() -> BybitEnvironment.resolve(spec));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("USE_SANDBOX")
        .hasMessageContaining("SPECIFIC_PARAM_TESTNET");
  }

  @Test
  public void productionUrlsFollowOfficialConnectivityContract() {
    BybitEnvironment environment = BybitEnvironment.PRODUCTION;
    assertThat(environment.getRestBaseUrl()).isEqualTo("https://api.bybit.com");
    assertThat(environment.getPublicWebsocketBaseUrl())
        .isEqualTo("wss://stream.bybit.com/v5/public/");
    assertThat(environment.getPrivateWebsocketUrl())
        .isEqualTo("wss://stream.bybit.com/v5/private");
    assertThat(environment.getTradeWebsocketUrl()).isEqualTo("wss://stream.bybit.com/v5/trade");
    assertThat(environment.supportsTradeWebsocket()).isTrue();
  }

  @Test
  public void demoUrlsFollowOfficialDemoContract() {
    BybitEnvironment environment = BybitEnvironment.DEMO;
    assertThat(environment.getRestBaseUrl()).isEqualTo("https://api-demo.bybit.com");
    // Demo public market data is identical to mainnet and served from the mainnet host.
    assertThat(environment.getPublicWebsocketBaseUrl())
        .isEqualTo("wss://stream.bybit.com/v5/public/");
    assertThat(environment.getPrivateWebsocketUrl())
        .isEqualTo("wss://stream-demo.bybit.com/v5/private");
    // The WebSocket order-entry (trade) transport is not supported in demo trading.
    assertThat(environment.getTradeWebsocketUrl()).isNull();
    assertThat(environment.supportsTradeWebsocket()).isFalse();
  }

  @Test
  public void testnetUrlsFollowOfficialConnectivityContract() {
    BybitEnvironment environment = BybitEnvironment.TESTNET;
    assertThat(environment.getRestBaseUrl()).isEqualTo("https://api-testnet.bybit.com");
    assertThat(environment.getPublicWebsocketBaseUrl())
        .isEqualTo("wss://stream-testnet.bybit.com/v5/public/");
    assertThat(environment.getPrivateWebsocketUrl())
        .isEqualTo("wss://stream-testnet.bybit.com/v5/private");
    assertThat(environment.getTradeWebsocketUrl())
        .isEqualTo("wss://stream-testnet.bybit.com/v5/trade");
    assertThat(environment.supportsTradeWebsocket()).isTrue();
  }

  @Test
  public void publicWebsocketUrlAppendsCategoryForEveryEnvironment() {
    for (BybitEnvironment environment : BybitEnvironment.values()) {
      assertThat(environment.getPublicWebsocketUrl(BybitCategory.SPOT))
          .isEqualTo(environment.getPublicWebsocketBaseUrl() + "spot");
      assertThat(environment.getPublicWebsocketUrl(BybitCategory.LINEAR))
          .isEqualTo(environment.getPublicWebsocketBaseUrl() + "linear");
      assertThat(environment.getPublicWebsocketUrl(BybitCategory.INVERSE))
          .isEqualTo(environment.getPublicWebsocketBaseUrl() + "inverse");
      assertThat(environment.getPublicWebsocketUrl(BybitCategory.OPTION))
          .isEqualTo(environment.getPublicWebsocketBaseUrl() + "option");
    }
  }
}
