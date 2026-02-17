package info.bitrich.xchangestream.kraken;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.knowm.xchange.ExchangeSpecification;

public class KrakenStreamingExchangeIT {

  public static StreamingExchange exchange;
  private static Throwable connectionFailure;

  @BeforeAll
  public static void setup() {
    ExchangeSpecification spec =
        StreamingExchangeFactory.INSTANCE
            .createExchangeWithoutSpecification(KrakenStreamingExchange.class)
            .getDefaultExchangeSpecification();
    spec.setApiKey(System.getProperty("apiKey"));
    spec.setSecretKey(System.getProperty("secretKey"));

    exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec);
    connectionFailure = null;
    try {
      exchange.connect().blockingAwait();
    } catch (Throwable t) {
      connectionFailure = t;
    }
  }

  @BeforeEach
  void exchangeReachable() {
    assumeTrue(
        connectionFailure == null,
        connectionFailure == null
            ? "Connected to Kraken websocket"
            : "Failed to connect to Kraken websocket: "
                + connectionFailure.getClass().getSimpleName()
                + ": "
                + connectionFailure.getMessage());
    assumeTrue(exchange != null && exchange.isAlive(), "Exchange websocket is unreachable");
  }

  @AfterAll
  public static void cleanup() {
    if (exchange != null && exchange.isAlive()) {
      exchange.disconnect().blockingAwait();
    }
  }
}
