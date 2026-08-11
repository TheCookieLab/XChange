package org.knowm.xchange.kucoin.uta;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.kucoin.KucoinApiMode;
import org.knowm.xchange.kucoin.KucoinExchange;

/**
 * WireMock base for deterministic UTA REST tests. The exchange is built in UTA mode against the
 * dynamic-port WireMock server; credentials are fixture values (signature validity is not asserted
 * by the mock).
 */
public class AbstractUtaResilienceTest {

  @RegisterExtension
  static WireMockExtension wireMockRule =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  protected KucoinExchange createUtaExchange() {
    KucoinExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(KucoinExchange.class);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setHost("localhost");
    specification.setSslUri("http://localhost:" + wireMockRule.getPort() + "/");
    specification.setPort(wireMockRule.getPort());
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");
    specification.setExchangeSpecificParametersItem("passphrase", "test-passphrase");
    specification.setExchangeSpecificParametersItem(
        KucoinExchange.API_MODE_PARAMETER, KucoinApiMode.UTA);
    exchange.applySpecification(specification);
    return exchange;
  }
}
