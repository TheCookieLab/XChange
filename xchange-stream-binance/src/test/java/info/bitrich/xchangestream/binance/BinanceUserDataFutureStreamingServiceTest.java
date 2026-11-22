package info.bitrich.xchangestream.binance;

import static info.bitrich.xchangestream.core.StreamingExchange.WS_CONNECTION_TIMEOUT;
import static info.bitrich.xchangestream.core.StreamingExchange.WS_IDLE_TIMEOUT;
import static info.bitrich.xchangestream.core.StreamingExchange.WS_RETRY_DURATION;
import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;

class BinanceUserDataFutureStreamingServiceTest {

  @Test
  void masksListenKeyInConnectionLogWithoutChangingWireUri() throws Exception {
    String baseUri = "wss://fstream.binance.com:9443/prefix/";
    String listenKey = "secret-listen-key";
    BinanceUserDataFutureStreamingService service = createService(baseUri, listenKey);

    assertThat(service.getLogSafeUri())
        .isEqualTo(URI.create("wss://fstream.binance.com:9443/prefix/ws/REDACTED"));
    assertThat(connectionUri(service)).isEqualTo(URI.create(baseUri + "ws/" + listenKey));
  }

  @Test
  void masksTheEntireListenKeyEvenWhenItContainsUriDelimiters() throws Exception {
    String baseUri = "wss://fstream.binance.com/";
    String listenKey = "secret/ws/part?token=more-secret#fragment";
    BinanceUserDataFutureStreamingService service = createService(baseUri, listenKey);

    assertThat(service.getLogSafeUri()).isEqualTo(URI.create(baseUri + "ws/REDACTED"));
    assertThat(service.getLogSafeUri().toString())
        .doesNotContain("secret", "token", "fragment");
    assertThat(connectionUri(service)).isEqualTo(URI.create(baseUri + "ws/" + listenKey));
  }

  private static BinanceUserDataFutureStreamingService createService(
      String baseUri, String listenKey) {
    ExchangeSpecification spec = new ExchangeSpecification(BinanceStreamingExchange.class);
    spec.setExchangeSpecificParametersItem(WS_CONNECTION_TIMEOUT, Duration.ofSeconds(10));
    spec.setExchangeSpecificParametersItem(WS_RETRY_DURATION, Duration.ofSeconds(5));
    spec.setExchangeSpecificParametersItem(WS_IDLE_TIMEOUT, 60);
    return BinanceUserDataFutureStreamingService.create(baseUri, listenKey, spec);
  }

  private static URI connectionUri(BinanceUserDataFutureStreamingService service)
      throws ReflectiveOperationException {
    Field field = NettyStreamingService.class.getDeclaredField("uri");
    field.setAccessible(true);
    return (URI) field.get(service);
  }
}
