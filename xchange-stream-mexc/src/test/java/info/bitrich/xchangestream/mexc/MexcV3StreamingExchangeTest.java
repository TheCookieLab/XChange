package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;

/** Streaming exchange wiring: default URI, connect lifecycle, and service accessors. */
class MexcV3StreamingExchangeTest {

  @Test
  void defaultWebsocketUriMatchesMexcDocs() {
    assertEquals("wss://wbs-api.mexc.com/ws", MexcV3StreamingExchange.DEFAULT_WEBSOCKET_URI);
  }

  @Test
  void connectCreatesServicesAndReportsConnectionFailure() throws IOException {
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);

    assertNotNull(exchange.getStreamingMarketDataService());
    assertFalse(exchange.isAlive());
    exchange.disconnect().onErrorComplete().blockingAwait();
  }
}
