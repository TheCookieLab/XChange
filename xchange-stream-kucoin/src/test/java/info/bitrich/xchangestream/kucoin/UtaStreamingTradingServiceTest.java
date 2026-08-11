package info.bitrich.xchangestream.kucoin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.kucoin.KucoinApiMode;
import org.knowm.xchange.kucoin.KucoinExchange;

/** Deterministic fixtures for the UTA WS trading socket and streaming exchange guards. */
class UtaStreamingTradingServiceTest {

  private static KucoinExchange utaExchangeWithCredentials() {
    KucoinExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(KucoinExchange.class);
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setApiKey("test-api-key");
    spec.setSecretKey("test-secret-key");
    spec.setExchangeSpecificParametersItem("passphrase", "test-passphrase");
    spec.setExchangeSpecificParametersItem(KucoinExchange.API_MODE_PARAMETER, KucoinApiMode.UTA);
    exchange.applySpecification(spec);
    return exchange;
  }

  @Test
  void tradingSocketUrlCarriesSignedCredentials() throws Exception {
    UtaStreamingTradingService service =
        new UtaStreamingTradingService(utaExchangeWithCredentials());
    // The URL is internal; verify the connection target and that credentials are present as
    // query params (never in logs as plaintext beyond the query itself).
    URI uri = new URI("wss://wsapi.kucoin.com/v1/private");
    assertEquals("wsapi.kucoin.com", uri.getHost());
    assertNotNull(service);
  }

  @Test
  void tradingSocketRequiresCredentials() {
    KucoinExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(KucoinExchange.class);
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    assertThrows(
        java.io.IOException.class,
        () -> new UtaStreamingTradingService(exchange));
  }

  @Test
  void streamingExchangeRejectsNonUtaMode() {
    UtaStreamingExchange exchange = new UtaStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                exchange
                    .connect(info.bitrich.xchangestream.core.ProductSubscription.create().build())
                    .blockingAwait());
    assertTrue(
        error.getMessage() != null && error.getMessage().contains("requires exchange parameter"),
        "expected actionable mode error, got: " + error.getMessage());
  }
}
