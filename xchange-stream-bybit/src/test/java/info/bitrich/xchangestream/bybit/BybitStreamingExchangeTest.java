package info.bitrich.xchangestream.bybit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_TESTNET;

import org.junit.Test;import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.config.BybitConfiguration;
import org.knowm.xchange.bybit.config.BybitEnvironment;
import org.knowm.xchange.bybit.dto.BybitCategory;

/**
 * Environment contract tests for the streaming exchange: every WebSocket transport must resolve
 * from the same validated configuration as the REST module, and unsupported combinations (demo
 * order-entry) must be rejected instead of silently rerouted.
 */
public class BybitStreamingExchangeTest {

  private BybitStreamingExchange exchangeWith(ExchangeSpecification spec) {
    BybitStreamingExchange exchange = new BybitStreamingExchange();
    exchange.applySpecification(spec);
    return exchange;
  }

  private ExchangeSpecification specification() {
    ExchangeSpecification spec = new BybitStreamingExchange().getDefaultExchangeSpecification();
    // applySpecification triggers remoteInit() when metadata loading is enabled; environment
    // resolution is a pure construction-time concern.
    spec.setShouldLoadRemoteMetaData(false);
    return spec;
  }

  private ExchangeSpecification authenticatedSpecification() {
    ExchangeSpecification spec = specification();
    spec.setApiKey("api-key");
    spec.setSecretKey("secret-key");
    return spec;
  }

  @Test
  public void productionConstructsAllThreeTransports() {
    BybitStreamingExchange exchange = exchangeWith(authenticatedSpecification());
    assertThat(exchange.getConfiguration().getEnvironment()).isEqualTo(BybitEnvironment.PRODUCTION);
    assertThat(exchange.isTradeTransportEnabled()).isTrue();
  }

  @Test
  public void testnetConstructsAllThreeTransports() {
    ExchangeSpecification spec = authenticatedSpecification();
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
    BybitStreamingExchange exchange = exchangeWith(spec);
    assertThat(exchange.getConfiguration().getEnvironment()).isEqualTo(BybitEnvironment.TESTNET);
    assertThat(exchange.isTradeTransportEnabled()).isTrue();
  }

  @Test
  public void demoSkipsOrderEntryTransport() {
    ExchangeSpecification spec = authenticatedSpecification();
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    BybitStreamingExchange exchange = exchangeWith(spec);
    assertThat(exchange.getConfiguration().getEnvironment()).isEqualTo(BybitEnvironment.DEMO);
    // Demo trading has no WebSocket order-entry transport; it must not silently use production.
    assertThat(exchange.isTradeTransportEnabled()).isFalse();
    // The trade-channel state observable degrades to an empty stream instead of NPE.
    assertThat(exchange.connectionStateObservableTradeChannel().isEmpty().blockingGet()).isTrue();
  }

  @Test
  public void conflictingEnvironmentFlagsAreRejectedBeforeServices() {
    ExchangeSpecification spec = authenticatedSpecification();
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
    Throwable thrown = catchThrowable(() -> new BybitStreamingExchange().applySpecification(spec));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting Bybit environments");
  }

  @Test
  public void missingStreamCategoryDefaultsToLinear() {
    // Historical behavior: a missing EXCHANGE_TYPE caused a NullPointerException during transport
    // construction. The validated configuration makes the default explicit instead.
    BybitStreamingExchange exchange = exchangeWith(specification());
    assertThat(BybitConfiguration.resolveStreamCategory(exchange.getExchangeSpecification()))
        .isEqualTo(BybitCategory.LINEAR);
    assertThat(exchange.getStreamingMarketDataService()).isNotNull();
  }

  @Test
  public void configuredStreamCategoryIsUsed() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(BybitConfiguration.EXCHANGE_TYPE, BybitCategory.SPOT);
    BybitStreamingExchange exchange = exchangeWith(spec);
    assertThat(BybitConfiguration.resolveStreamCategory(exchange.getExchangeSpecification()))
        .isEqualTo(BybitCategory.SPOT);
  }

  @Test
  public void legacyExchangeTypeConstantIsCompatible() {
    assertThat(BybitStreamingExchange.EXCHANGE_TYPE).isEqualTo(BybitConfiguration.EXCHANGE_TYPE);
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(BybitStreamingExchange.EXCHANGE_TYPE, BybitCategory.SPOT);
    assertThat(BybitConfiguration.resolveStreamCategory(spec)).isEqualTo(BybitCategory.SPOT);
  }
}
