package org.knowm.xchange.mexc.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.mexc.v3.config.MexcV3Configuration;
import org.knowm.xchange.mexc.v3.service.MexcV3AccountService;
import org.knowm.xchange.mexc.v3.service.MexcV3MarketDataService;
import org.knowm.xchange.mexc.v3.service.MexcV3TradeService;

/** Default-specification routing and service wiring for the MEXC Spot v3 adapter. */
public class MexcV3ExchangeTest {

  private MexcV3Exchange createExchange() {
    return (MexcV3Exchange)
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(MexcV3Exchange.class);
  }

  @Test
  public void defaultSpecificationRoutesToOfficialApi() {
    MexcV3Exchange exchange = createExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();

    assertThat(spec.getSslUri()).isEqualTo("https://api.mexc.com");
    assertThat(spec.getHost()).isEqualTo("api.mexc.com");
    assertThat(spec.getPort()).isEqualTo(443);
    assertThat(spec.getExchangeName()).isEqualTo("MEXC");
  }

  @Test
  public void restBaseUrlOverrideDrivesSslUriAndConcludesHost() {
    MexcV3Exchange exchange = createExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(MexcV3Configuration.REST_BASE_URL_KEY, "https://mexc.example.test");

    exchange.applySpecification(spec);

    assertThat(spec.getSslUri()).isEqualTo("https://mexc.example.test");
    assertThat(spec.getHost()).isEqualTo("mexc.example.test");
    assertThat(spec.getPort()).isEqualTo(443);
    assertThat(exchange.getConfiguration().getRestBaseUrl()).isEqualTo("https://mexc.example.test");
  }

  @Test
  public void nonHttpsBaseUrlOverrideIsRejected() {
    MexcV3Exchange exchange = createExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(MexcV3Configuration.REST_BASE_URL_KEY, "http://mexc.local:8080");

    assertThatThrownBy(() -> exchange.applySpecification(spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not an https URL");
  }

  @Test
  public void applySpecificationWiresAllServices() {
    MexcV3Exchange exchange = createExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();

    exchange.applySpecification(spec);

    assertThat(exchange.getMarketDataService()).isInstanceOf(MexcV3MarketDataService.class);
    assertThat(exchange.getAccountService()).isInstanceOf(MexcV3AccountService.class);
    assertThat(exchange.getTradeService()).isInstanceOf(MexcV3TradeService.class);
    assertThat(exchange.getConfiguration().getRestBaseUrl()).isEqualTo("https://api.mexc.com");
    assertThat(exchange.getConfiguration().getStreamBaseUrl())
        .isEqualTo("wss://wbs-api.mexc.com/ws");
    assertThat(exchange.getConfiguration().getRecvWindowMs()).isEqualTo(5000);
  }
}
