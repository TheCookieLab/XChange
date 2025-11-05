package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import info.bitrich.xchangestream.core.ProductSubscription;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;

class CoinbaseSandboxConnectivityTest {

  @Test
  void sandboxWebsocketSmoke() {
    assumeTrue(
        sandboxSmokeEnabled(),
        () ->
            "Sandbox smoke test disabled. Set COINBASE_SANDBOX_SMOKE=true or "
                + "-Dcoinbase.sandbox.smoke=true to enable.");

    CoinbaseStreamingExchange exchange = new CoinbaseStreamingExchange();
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_SANDBOX, true);
    exchange.applySpecification(specification);

    try {
      assertDoesNotThrow(
          () ->
              exchange
                  .connect(ProductSubscription.create().build())
                  .timeout(20, TimeUnit.SECONDS)
                  .blockingAwait());
      assertTrue(exchange.isAlive(), "Expected sandbox websocket to be reachable");
    } finally {
      assertDoesNotThrow(
          () ->
              exchange
                  .disconnect()
                  .timeout(10, TimeUnit.SECONDS)
                  .blockingAwait());
    }
  }

  private static boolean sandboxSmokeEnabled() {
    return parseFlag(System.getProperty("coinbase.sandbox.smoke"))
        || parseFlag(System.getenv("COINBASE_SANDBOX_SMOKE"));
  }

  private static boolean parseFlag(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase();
    return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
  }
}
