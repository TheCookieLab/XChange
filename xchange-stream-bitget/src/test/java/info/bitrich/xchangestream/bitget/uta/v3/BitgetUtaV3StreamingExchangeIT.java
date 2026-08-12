package info.bitrich.xchangestream.bitget.uta.v3;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import info.bitrich.xchangestream.bitget.BitgetStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitget.config.BitgetApiMode;
import org.knowm.xchange.bitget.config.BitgetConfiguration;

/**
 * Live smoke test for the Bitget UTA v3 WebSocket integration.
 *
 * <p>Runs only when credentials are supplied ({@code -DapiKey=... -DsecretKey=...
 * -Dpassphrase=...}) and the exchange is reachable; otherwise it is skipped, like the classic v2
 * ITs.
 */
class BitgetUtaV3StreamingExchangeIT {

  public static StreamingExchange exchange;

  @BeforeAll
  public static void setup() {
    ExchangeSpecification spec =
        StreamingExchangeFactory.INSTANCE
            .createExchangeWithoutSpecification(BitgetStreamingExchange.class)
            .getDefaultExchangeSpecification();
    spec.setApiKey(System.getProperty("apiKey"));
    spec.setSecretKey(System.getProperty("secretKey"));
    spec.setPassword(System.getProperty("passphrase"));
    spec.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);

    exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec);

    exchange.connect().blockingAwait(30, TimeUnit.SECONDS);
  }

  @BeforeEach
  void exchangeReachable() {
    assumeTrue(exchange.isAlive(), "Exchange is unreachable");
  }

  @AfterAll
  public static void cleanup() {
    if (exchange != null && exchange.isAlive()) {
      exchange.disconnect().blockingAwait();
    }
  }

  @Test
  void connectsAndExposesUtaV3Services() {
    assumeTrue(exchange.isAlive(), "Exchange is unreachable");
    org.assertj.core.api.Assertions.assertThat(exchange.getStreamingMarketDataService())
        .isInstanceOf(BitgetUtaV3StreamingMarketDataService.class);
    // private channels only exist when credentials were supplied
    if (System.getProperty("secretKey") != null) {
      org.assertj.core.api.Assertions.assertThat(exchange.getStreamingTradeService())
          .isInstanceOf(BitgetUtaV3StreamingTradeService.class);
      org.assertj.core.api.Assertions.assertThat(exchange.getStreamingAccountService())
          .isInstanceOf(BitgetUtaV3StreamingAccountService.class);
    }
  }
}
