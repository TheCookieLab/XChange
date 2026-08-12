package info.bitrich.xchangestream.bitget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitget.config.BitgetApiMode;
import org.knowm.xchange.bitget.config.BitgetConfiguration;

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
  void utaV3ReconnectCompositionDefersServiceReplacementUntilSubscription() throws Exception {
    BitgetStreamingExchange exchange = new BitgetStreamingExchange();
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    // hermetic: without this, applySpecification triggers remoteInitUtaV3(), a live instrument
    // fetch that pollutes the shared Currency registry and breaks later tests
    specification.setShouldLoadRemoteMetaData(false);
    specification.setExchangeSpecificParametersItem(
        BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);
    exchange.applySpecification(specification);

    BitgetUtaV3StreamingService oldPublic =
        new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL);
    setServices(
        exchange,
        oldPublic,
        null,
        new BitgetUtaV3StreamingMarketDataService(oldPublic),
        null,
        null);

    // the standard reconnect idiom: composing it must not touch the live holder
    exchange.disconnect().andThen(exchange.connect());

    assertThat(exchange.getPublicNettyStreamingService())
        .as(
            "disconnect().andThen(connect()) must resolve the OLD transports at subscription; an "
                + "eager connect would replace the holder while the disconnect Completable is still "
                + "unsubscribed and shut down the fresh unopened sockets instead of the live ones")
        .isSameAs(oldPublic);
  }

  @Test
  void disconnectCompletionPreservesAConcurrentlyInstalledHolder() throws Exception {
    BitgetStreamingExchange exchange = new BitgetStreamingExchange();
    CompletableSubject shutdownGate = CompletableSubject.create();
    BitgetUtaV3StreamingService hangingService =
        new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL) {
          @Override
          public Completable disconnect() {
            return shutdownGate;
          }
        };
    setServices(
        exchange,
        hangingService,
        null,
        new BitgetUtaV3StreamingMarketDataService(hangingService),
        null,
        null);

    // start a disconnect whose transport shutdown is still in flight
    TestObserver<Void> disconnect = exchange.disconnect().test();

    // a concurrent connect() installs a fresh holder while the old shutdown is completing
    BitgetUtaV3StreamingService newPublic = new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL);
    setServices(
        exchange,
        newPublic,
        null,
        new BitgetUtaV3StreamingMarketDataService(newPublic),
        null,
        null);

    // the old transport finally finishes shutting down
    shutdownGate.onComplete();
    disconnect.assertComplete();

    assertThat(exchange.getPublicNettyStreamingService())
        .as(
            "a disconnect completing after a concurrent connect installed a fresh holder must not "
                + "wipe it — the new sockets would stay live but unreachable through every getter")
        .isSameAs(newPublic);
  }

  @Test
  void classicReconnectCompositionDefersServiceReplacementUntilSubscription() throws Exception {
    BitgetStreamingExchange exchange = new BitgetStreamingExchange();
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    // hermetic: without this, applySpecification triggers remoteInit, a live catalog fetch
    specification.setShouldLoadRemoteMetaData(false);
    // no API_MODE parameter: the default is CLASSIC_V2
    exchange.applySpecification(specification);

    BitgetStreamingService oldPublic = new BitgetStreamingService(Config.V2_PUBLIC_WS_URL);
    setServices(
        exchange,
        oldPublic,
        null,
        new BitgetStreamingMarketDataService(oldPublic),
        null,
        null);

    // the standard reconnect idiom: composing it must not touch the live holder, exactly like the
    // UTA V3 path — an eager classic connect would replace services during composition and the
    // cold disconnect would then shut down the fresh unopened sockets instead of the live ones
    exchange.disconnect().andThen(exchange.connect());

    assertThat(exchange.getPublicNettyStreamingService())
        .as(
            "classic-mode disconnect().andThen(connect()) must defer service replacement until "
                + "subscription")
        .isSameAs(oldPublic);
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

  @Test
  void classicConnectFailureStillInstallsHolderSoDisconnectCanStopTheTransport() throws Exception {
    BitgetStreamingExchange exchange =
        new BitgetStreamingExchange() {
          @Override
          protected BitgetPrivateStreamingService createClassicPrivateService(
              ExchangeSpecification specification) {
            return new BitgetPrivateStreamingService(
                Config.V2_PRIVATE_WS_URL,
                specification.getApiKey(),
                specification.getSecretKey(),
                specification.getPassword()) {
              @Override
              public Completable connect() {
                // deterministic stand-in for a DNS/TCP/TLS/handshake failure; NettyStreamingService
                // would otherwise schedule an automatic reconnect loop
                return Completable.error(new IOException("connection refused"));
              }
            };
          }
        };
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    // hermetic: without this, applySpecification triggers remoteInitUtaV3(), a live instrument
    // fetch that pollutes the shared Currency registry and breaks later tests
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("api-key");
    specification.setSecretKey("api-secret");
    specification.setPassword("api-passphrase");
    exchange.applySpecification(specification);

    Completable connect = exchange.connect();

    assertThatThrownBy(connect::blockingAwait)
        .as("the classic path awaits the private connection and must surface its failure")
        .isInstanceOf(Throwable.class);
    // the private transport failed to connect, but the holder was installed BEFORE the await, so
    // a subsequent disconnect() can stop its reconnect loop instead of leaking the event loop
    assertThat(exchange.getPrivateNettyStreamingService())
        .as("holder must be installed before awaiting the private connection")
        .isNotNull();
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
    // the holder is an AtomicReference<StreamingServices> and StreamingServices is a private
    // nested class, so the atomic set is invoked reflectively instead of with an unchecked cast
    AtomicReference.class.getMethod("set", Object.class).invoke(servicesField.get(exchange), holder);
  }
}
