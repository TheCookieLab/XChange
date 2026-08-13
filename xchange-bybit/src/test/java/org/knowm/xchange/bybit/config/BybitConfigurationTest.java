package org.knowm.xchange.bybit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_TESTNET;

import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;

public class BybitConfigurationTest {

  private ExchangeSpecification specification() {
    return new BybitExchange().getDefaultExchangeSpecification();
  }

  @Test
  public void fromResolvesProductionAndUnifiedByDefault() {
    BybitConfiguration configuration = BybitConfiguration.from(specification());
    assertThat(configuration.getEnvironment()).isEqualTo(BybitEnvironment.PRODUCTION);
    assertThat(configuration.getAccountType()).isEqualTo(BybitAccountType.UNIFIED);
  }

  @Test
  public void fromResolvesConfiguredEnvironmentAndAccountType() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.UNIFIED);
    BybitConfiguration configuration = BybitConfiguration.from(spec);
    assertThat(configuration.getEnvironment()).isEqualTo(BybitEnvironment.DEMO);
    assertThat(configuration.getAccountType()).isEqualTo(BybitAccountType.UNIFIED);

    spec = specification();
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
    spec.setExchangeSpecificParametersItem(
        SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.CONTRACT);
    configuration = BybitConfiguration.from(spec);
    assertThat(configuration.getEnvironment()).isEqualTo(BybitEnvironment.TESTNET);
    assertThat(configuration.getAccountType()).isEqualTo(BybitAccountType.CONTRACT);
  }

  @Test
  public void fromRejectsUnsupportedAccountTypeValue() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_ACCOUNT_TYPE, "UNIFIED");
    Throwable thrown = catchThrowable(() -> BybitConfiguration.from(spec));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported Bybit account type")
        .hasMessageContaining("BybitAccountType");
  }

  @Test
  public void resolveStreamCategoryDefaultsToLinear() {
    assertThat(BybitConfiguration.resolveStreamCategory(specification()))
        .isEqualTo(BybitCategory.LINEAR);
  }

  @Test
  public void resolveStreamCategoryUsesConfiguredCategory() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(BybitConfiguration.EXCHANGE_TYPE, BybitCategory.SPOT);
    assertThat(BybitConfiguration.resolveStreamCategory(spec)).isEqualTo(BybitCategory.SPOT);
  }

  @Test
  public void resolveStreamCategoryRejectsUnsupportedValue() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(BybitConfiguration.EXCHANGE_TYPE, "spot");
    Throwable thrown = catchThrowable(() -> BybitConfiguration.resolveStreamCategory(spec));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported Bybit stream category")
        .hasMessageContaining("BybitCategory");
  }

  @Test
  public void fromPropagatesConflictingEnvironmentFlags() {
    ExchangeSpecification spec = specification();
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    spec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
    Throwable thrown = catchThrowable(() -> BybitConfiguration.from(spec));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting Bybit environments");
  }
}
