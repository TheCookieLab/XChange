package info.bitrich.xchangestream.bitget;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3PrivateStreamingService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingAccountService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingMarketDataService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingTradeService;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.reactivex.rxjava3.core.Completable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/** Lifecycle behaviour of {@link BitgetStreamingExchange}. */
class BitgetStreamingExchangeTest {

  @Test
  void disconnectClearsTransportAndWrapperFields() throws Exception {
    BitgetStreamingExchange exchange = new BitgetStreamingExchange();
    BitgetUtaV3StreamingService publicService =
        new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL);
    BitgetUtaV3PrivateStreamingService privateService =
        new BitgetUtaV3PrivateStreamingService(
            Config.V3_PRIVATE_WS_URL, "api-key", "api-secret", "api-password");
    setServices(
        exchange,
        publicService,
        privateService,
        new BitgetUtaV3StreamingMarketDataService(publicService),
        new BitgetUtaV3StreamingTradeService(privateService, null),
        new BitgetUtaV3StreamingAccountService(privateService));

    // UTA V3 transports are not classic-mode services, so the legacy getters stay null and the
    // mode-agnostic accessors expose the wired sockets
    assertThat(exchange.getPublicStreamingService()).isNull();
    assertThat(exchange.getPrivateStreamingService()).isNull();
    assertThat(exchange.getPublicNettyStreamingService()).isSameAs(publicService);
    assertThat(exchange.getPrivateNettyStreamingService()).isSameAs(privateService);

    exchange.disconnect().blockingAwait();

    assertThat(exchange.getPublicNettyStreamingService()).isNull();
    assertThat(exchange.getPrivateNettyStreamingService()).isNull();
    assertThat(exchange.getStreamingMarketDataService()).isNull();
    assertThat(exchange.getStreamingTradeService()).isNull();
    assertThat(exchange.getStreamingAccountService()).isNull();
    assertThat(exchange.isAlive())
        .as("with the transport fields cleared, the exchange must report dead")
        .isFalse();
  }

  @Test
  void disconnectWithoutSubscriptionKeepsReferencesUntilItExecutes() throws Exception {
    BitgetStreamingExchange exchange = new BitgetStreamingExchange();
    BitgetUtaV3StreamingService publicService =
        new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL);
    BitgetUtaV3PrivateStreamingService privateService =
        new BitgetUtaV3PrivateStreamingService(
            Config.V3_PRIVATE_WS_URL, "api-key", "api-secret", "api-password");
    setServices(
        exchange,
        publicService,
        privateService,
        new BitgetUtaV3StreamingMarketDataService(publicService),
        new BitgetUtaV3StreamingTradeService(privateService, null),
        new BitgetUtaV3StreamingAccountService(privateService));

    Completable disconnect = exchange.disconnect();

    assertThat(exchange.getPublicNettyStreamingService())
        .as("a cold disconnect must not clear the references before it executes")
        .isSameAs(publicService);
    assertThat(exchange.getPrivateNettyStreamingService()).isSameAs(privateService);

    disconnect.blockingAwait();

    assertThat(exchange.getPublicNettyStreamingService()).isNull();
    assertThat(exchange.getPrivateNettyStreamingService()).isNull();
  }

  @Test
  void classicModeGetterDescriptorsExposeConcreteServices() throws Exception {
    BitgetStreamingExchange exchange = new BitgetStreamingExchange();
    BitgetStreamingService publicService = new BitgetStreamingService(Config.V2_PUBLIC_WS_URL);
    BitgetPrivateStreamingService privateService =
        new BitgetPrivateStreamingService(
            Config.V2_PRIVATE_WS_URL, "api-key", "api-secret", "api-password");
    setServices(
        exchange,
        publicService,
        privateService,
        new BitgetStreamingMarketDataService(publicService),
        new BitgetStreamingTradeService(privateService),
        null);

    assertThat(exchange.getPublicStreamingService())
        .as("the legacy getter must keep returning the concrete classic service")
        .isSameAs(publicService);
    assertThat(exchange.getPrivateStreamingService()).isSameAs(privateService);
  }

  @Test
  void disconnectOnCleanExchangeCompletesImmediately() {
    BitgetStreamingExchange exchange = new BitgetStreamingExchange();

    exchange.disconnect().blockingAwait();

    assertThat(exchange.getPublicNettyStreamingService()).isNull();
    assertThat(exchange.getPrivateNettyStreamingService()).isNull();
  }

  private static void setServices(
      BitgetStreamingExchange exchange,
      NettyStreamingService<?> publicService,
      NettyStreamingService<?> privateService,
      StreamingMarketDataService marketDataService,
      StreamingTradeService tradeService,
      StreamingAccountService accountService)
      throws Exception {
    Class<?> holderClass =
        Class.forName("info.bitrich.xchangestream.bitget.BitgetStreamingExchange$StreamingServices");
    Constructor<?> constructor =
        holderClass.getDeclaredConstructor(
            NettyStreamingService.class,
            NettyStreamingService.class,
            StreamingMarketDataService.class,
            StreamingTradeService.class,
            StreamingAccountService.class);
    constructor.setAccessible(true);
    Object holder =
        constructor.newInstance(
            publicService, privateService, marketDataService, tradeService, accountService);
    Field servicesField = BitgetStreamingExchange.class.getDeclaredField("services");
    servicesField.setAccessible(true);
    servicesField.set(exchange, holder);
  }
}
