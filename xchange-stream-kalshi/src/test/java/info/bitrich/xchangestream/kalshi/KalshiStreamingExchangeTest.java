package info.bitrich.xchangestream.kalshi;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/**
 * Credential fail-fast contract for {@link KalshiStreamingExchange}: Kalshi authenticates every
 * WebSocket session, public market-data channels included, so {@code applySpecification} must
 * reject a specification that lacks either credential half before any service is built.
 */
class KalshiStreamingExchangeTest {

  @Test
  void applySpecificationWithoutCredentialsFailsFast() {
    KalshiStreamingExchange exchange = new KalshiStreamingExchange();
    ExchangeSpecification spec = new ExchangeSpecification(KalshiStreamingExchange.class);

    ExchangeSecurityException error =
        assertThrows(
            ExchangeSecurityException.class, () -> exchange.applySpecification(spec));
    assertTrue(error.getMessage().contains("apiKey"));
  }

  @Test
  void applySpecificationWithOnlyTheApiKeyStillFailsFast() {
    KalshiStreamingExchange exchange = new KalshiStreamingExchange();
    ExchangeSpecification spec = new ExchangeSpecification(KalshiStreamingExchange.class);
    spec.setApiKey("key-id");

    ExchangeSecurityException error =
        assertThrows(
            ExchangeSecurityException.class, () -> exchange.applySpecification(spec));
    assertTrue(error.getMessage().contains("secretKey"));
  }
}
