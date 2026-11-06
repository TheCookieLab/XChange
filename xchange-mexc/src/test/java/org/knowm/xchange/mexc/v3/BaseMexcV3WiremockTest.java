package org.knowm.xchange.mexc.v3;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.mexc.v3.config.MexcV3Configuration;

/** Shared WireMock harness for MEXC Spot v3 tests: local HTTP server, no live provider. */
public class BaseMexcV3WiremockTest {

  @RegisterExtension
  public WireMockExtension wireMockRule =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort())
          .configureStaticDsl(true)
          .build();

  /** Builds a configured exchange pointed at the local WireMock server. */
  public MexcV3Exchange createExchange() throws IOException {
    MexcV3Exchange exchange =
        (MexcV3Exchange)
            ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(MexcV3Exchange.class);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setHost("localhost");
    specification.setSslUri("http://localhost:" + wireMockRule.getPort());
    specification.setPort(wireMockRule.getPort());
    specification.setApiKey("test_api_key");
    specification.setSecretKey("test_secret_key");
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);
    return exchange;
  }
}
