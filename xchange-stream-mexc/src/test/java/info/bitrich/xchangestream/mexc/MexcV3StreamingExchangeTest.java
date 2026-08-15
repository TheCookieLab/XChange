package info.bitrich.xchangestream.mexc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.mexc.v3.config.MexcV3Configuration;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;

/** Streaming exchange wiring: default URI, connect lifecycle, and service accessors. */
class MexcV3StreamingExchangeTest {

  private WireMockServer wireMock;

  @AfterEach
  void tearDown() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  void privateStreamAccessorsRequireApiKey() {
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);

    assertThrows(ExchangeSecurityException.class, () -> exchange.getStreamingAccountService());
    assertThrows(ExchangeSecurityException.class, () -> exchange.getStreamingTradeService());

    spec.setApiKey("test-key");
    spec.setSecretKey("test-secret");
    exchange.applySpecification(spec);
    assertDoesNotThrow(() -> exchange.getStreamingAccountService());
    assertDoesNotThrow(() -> exchange.getStreamingTradeService());
  }

  @Test
  void defaultWebsocketUriMatchesMexcDocs() {
    assertEquals("wss://wbs-api.mexc.com/ws", MexcV3StreamingExchange.DEFAULT_WEBSOCKET_URI);
  }

  @Test
  void typedStreamBaseUrlControlsConnectionEndpoint() throws Exception {
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3Configuration.STREAM_BASE_URL_KEY, "wss://127.0.0.1:1/typed");
    exchange.applySpecification(spec);

    TestObserver<Void> observer = exchange.connect().test();
    Field serviceField = MexcV3StreamingExchange.class.getDeclaredField("streamingService");
    serviceField.setAccessible(true);
    MexcV3StreamingService service =
        (MexcV3StreamingService) serviceField.get(exchange);
    Field uriField = NettyStreamingService.class.getDeclaredField("uri");
    uriField.setAccessible(true);
    assertEquals(URI.create("wss://127.0.0.1:1/typed"), uriField.get(service));

    observer.dispose();
    exchange.disconnect().onErrorComplete().blockingAwait();
  }
  @Test
  void typedStreamBaseUrlTakesPrecedenceOverLegacyWebsocketUri() throws Exception {
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3Configuration.STREAM_BASE_URL_KEY, "wss://127.0.0.1:1/typed");
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/legacy");
    exchange.applySpecification(spec);

    TestObserver<Void> observer = exchange.connect().test();
    Field serviceField = MexcV3StreamingExchange.class.getDeclaredField("streamingService");
    serviceField.setAccessible(true);
    MexcV3StreamingService service =
        (MexcV3StreamingService) serviceField.get(exchange);
    Field uriField = NettyStreamingService.class.getDeclaredField("uri");
    uriField.setAccessible(true);
    assertEquals(URI.create("wss://127.0.0.1:1/typed"), uriField.get(service));

    observer.dispose();
    exchange.disconnect().onErrorComplete().blockingAwait();
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

  @Test
  void connectWithApiKeyCreatesListenKeyAndClosesItOnDisconnect() {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);

    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
    assertNotNull(exchange.getStreamingAccountService());
    assertNotNull(exchange.getStreamingTradeService());

    exchange.disconnect().onErrorComplete().blockingAwait();

    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key")));
  }

  @Test
  void subscribingTheConnectChainTwiceCreatesOnlyOneListenKey() {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    // The same connect chain subscribed twice must not create a second listen key: the first
    // would be orphaned until its 60-minute expiry and repeated subscriptions could accumulate
    // keys up to MEXC's per-user limit. Both subscriptions fail on the unreachable socket.
    Completable connect = exchange.connect();
    connect.test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    connect.test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);

    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
  }

  @Test
  void resubscribingAConnectedChainKeepsTheTransportAndListenKey() throws Exception {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    Completable connect = exchange.connect();
    connect.test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);

    // Simulate a connected transport: subscribing the same chain again must then complete
    // without rebuilding the service. buildStreamingService disconnects the current transport
    // and replaces all facades, which would tear down the active streams; the listen key must
    // also stay at one.
    Object serviceBefore = streamingServiceInstance(exchange);
    forceSocketOpen(serviceBefore);
    connect.test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

    assertSame(serviceBefore, streamingServiceInstance(exchange));
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
  }

  private static Object streamingServiceInstance(MexcV3StreamingExchange exchange)
      throws Exception {
    java.lang.reflect.Field serviceField =
        MexcV3StreamingExchange.class.getDeclaredField("streamingService");
    serviceField.setAccessible(true);
    return serviceField.get(exchange);
  }

  private static void forceSocketOpen(Object service) throws Exception {
    java.lang.reflect.Field channelField =
        NettyStreamingService.class.getDeclaredField("webSocketChannel");
    channelField.setAccessible(true);
    channelField.set(service, new io.netty.channel.embedded.EmbeddedChannel());
  }

  @Test
  void keepaliveTransientFailureRetriesAndScheduleSurvives() {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));
    wireMock.stubFor(
        put(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key"))
            .willReturn(aResponse().withStatus(500)));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);
    exchange.keepAliveIntervalSeconds = 1L;
    TestScheduler keepAliveTicks = new TestScheduler();
    exchange.keepAliveScheduler = keepAliveTicks;

    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);

    // Every refresh tick fails (500) after KEEPALIVE_ATTEMPTS bounded retries; the schedule must
    // survive so later ticks keep retrying instead of dying on the first failure. Ticks are
    // driven deterministically by the TestScheduler (each advance fires the next cadence signal
    // once the previous tick's work completed); the bounded real waits below only cover the
    // inherently asynchronous real HTTP requests to WireMock.
    int keepaliveCalls = countKeepaliveCalls();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (keepaliveCalls < MexcV3StreamingExchange.KEEPALIVE_ATTEMPTS * 2L && System.nanoTime() < deadline) {
      keepAliveTicks.advanceTimeBy(1, TimeUnit.SECONDS);
      sleepQuietly(50L);
      keepaliveCalls = countKeepaliveCalls();
    }
    assertTrue(
        keepaliveCalls >= MexcV3StreamingExchange.KEEPALIVE_ATTEMPTS * 2L,
        "keepalive schedule must survive failing ticks, saw " + keepaliveCalls + " PUTs");

    exchange.disconnect().onErrorComplete().blockingAwait();

    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key")));
  }

  @Test
  void retriedConnectReusesExistingListenKey() {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);
    // Never advance: the keepalive schedule must not fire during this test.
    exchange.keepAliveScheduler = new TestScheduler();

    // First attempt: listen key created, WebSocket connect fails.
    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));

    // A caller retry must reuse the existing key (and its keepalive) instead of creating a
    // second key; creating a new key per retry would orphan the previous one until its 60-minute
    // expiry and could accumulate keys up to MEXC's per-user limit.
    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
    wireMock.verify(
        0, putRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key")));

    exchange.disconnect().onErrorComplete().blockingAwait();

    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key")));
  }

  private int countKeepaliveCalls() {
    // WireMock 3's RequestMethod.fromString always creates a new instance, so `==` against the
    // static PUT constant never matches; findAll uses the same matching machinery as verify.
    return wireMock
        .findAll(putRequestedFor(urlPathEqualTo("/api/v3/userDataStream")))
        .size();
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void connectWithoutApiKeyNeverCallsUserDataStream() throws IOException {    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);

    wireMock.verify(0, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
    exchange.disconnect().onErrorComplete().blockingAwait();
  }

  @Test
  void constructingConnectWithoutSubscribingBuildsNoTransport() throws Exception {
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    Completable first = exchange.connect();
    Completable second = exchange.connect();

    // connect() is a cold Completable factory: constructing it must not build (and thereby
    // replace/disconnect) a transport. Otherwise composing two connection Completables before
    // subscribing either one would let the second construction tear down the first's transport
    // and orphan its socket.
    assertNull(streamingServiceInstance(exchange));

    // Subscribing a chain builds the transport and connects it (connection refused here).
    first.test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    Object serviceAfterFirst = streamingServiceInstance(exchange);
    assertNotNull(serviceAfterFirst);

    // A live transport is never replaced by a sibling chain: subscribing the second chain
    // completes without rebuilding, which would disconnect the active streams.
    forceSocketOpen(serviceAfterFirst);
    second.test().awaitDone(10, TimeUnit.SECONDS).assertComplete();
    assertSame(serviceAfterFirst, streamingServiceInstance(exchange));
    exchange.disconnect().onErrorComplete().blockingAwait();
  }

  @Test
  void constructingDisconnectWithoutSubscribingKeepsKeyAndKeepalive() throws Exception {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));

    // An unsubscribed disconnect must not stop the keepalive or discard the key reference: an
    // abandoned disconnect would leave the socket open with a key nobody refreshes (it expires
    // after 60 minutes), and a later disconnect could not close that key because its reference
    // was already discarded.
    Completable disconnect = exchange.disconnect();
    assertEquals("test-listen-key", listenKeyInstance(exchange));
    assertNotNull(keepAliveDisposableInstance(exchange));

    // Subscribing the constructed disconnect executes the real lifecycle: keepalive stops, the
    // key is closed exactly once, and the transport disconnects.
    disconnect.onErrorComplete().blockingAwait();
    assertNull(keepAliveDisposableInstance(exchange));
    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key")));
  }

  private static String listenKeyInstance(MexcV3StreamingExchange exchange) throws Exception {
    java.lang.reflect.Field keyField = MexcV3StreamingExchange.class.getDeclaredField("listenKey");
    keyField.setAccessible(true);
    return (String) keyField.get(exchange);
  }

  private static Object keepAliveDisposableInstance(MexcV3StreamingExchange exchange)
      throws Exception {
    java.lang.reflect.Field disposableField =
        MexcV3StreamingExchange.class.getDeclaredField("keepAliveDisposable");
    disposableField.setAccessible(true);
    return disposableField.get(exchange);
  }
}
