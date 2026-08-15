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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void simultaneousConnectSubscriptionsShareOneInFlightAttempt() throws Exception {
    // RFC 5737 TEST-NET addresses are unroutable: the TCP connect stays pending until the
    // connect timeout, so the attempt is deterministically in flight and isAlive() false
    // while both chains subscribe.
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://192.0.2.1:80/ws");
    exchange.applySpecification(spec);

    Completable first = exchange.connect();
    first.test(); // builds the transport; the attempt stays in flight (connect pending)
    Object serviceAfterFirst = streamingServiceInstance(exchange);
    assertNotNull(serviceAfterFirst);

    // A second subscription while the first attempt is in flight must share it: building
    // again would disconnect the in-flight transport and leave the first caller connected
    // to an orphaned service.
    Completable second = exchange.connect();
    second.test();
    assertSame(serviceAfterFirst, streamingServiceInstance(exchange));

    exchange.disconnect().onErrorComplete().blockingAwait();
  }

  @Test
  void keyedStreamUriAdoptsSuppliedKeyIntoLifecycle() throws Exception {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"created-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=supplied-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"supplied-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI,
        "ws://127.0.0.1:1/ws?listenKey=supplied-key");
    exchange.applySpecification(spec);

    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);

    // The supplied key is adopted into the lifecycle: no key is created, the keepalive is
    // scheduled (the key would expire after 60 minutes without it), and disconnect closes
    // exactly the supplied key.
    assertEquals("supplied-key", listenKeyInstance(exchange));
    assertNotNull(keepAliveDisposableInstance(exchange));
    wireMock.verify(0, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));

    exchange.disconnect().onErrorComplete().blockingAwait();
    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=supplied-key")));
  }

  @Test
  void emptyListenKeyInUriFallsBackToCreatingAKey() throws Exception {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"created-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=created-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"created-key\"}")));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test_api_key");
    spec.setSecretKey("test_secret_key");
    spec.setHost("localhost");
    spec.setSslUri("http://localhost:" + wireMock.port());
    spec.setPort(wireMock.port());
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws?listenKey=");
    exchange.applySpecification(spec);

    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);

    // The empty parameter must not be adopted as a supplied key: there would be nothing to
    // refresh or close, and the socket would connect without authorization. A key is created
    // instead, and the normal private lifecycle (keepalive, close on disconnect) applies.
    assertEquals("created-key", listenKeyInstance(exchange));
    assertNotNull(keepAliveDisposableInstance(exchange));
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));

    exchange.disconnect().onErrorComplete().blockingAwait();
    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=created-key")));
  }

  @Test
  void disconnectCancelsAnInFlightPrivateConnectionAttempt() throws Exception {
    // Deterministic seam: the key-creation POST blocks inside a WireMock response transformer
    // until the test releases it. No fixed server delay or polling is involved — the test
    // observes the exact moment the POST is in flight (postStarted) and resolves it only after
    // the disconnect has been launched.
    CountDownLatch postStarted = new CountDownLatch(1);
    CountDownLatch releasePost = new CountDownLatch(1);
    wireMock =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .extensions(
                    new ResponseTransformer() {
                      @Override
                      public Response transform(
                          Request request, Response response, FileSource files, Parameters p) {
                        if ("POST".equals(request.getMethod().value())
                            && "/api/v3/userDataStream".equals(request.getUrl())) {
                          postStarted.countDown();
                          try {
                            if (!releasePost.await(10, TimeUnit.SECONDS)) {
                              throw new IllegalStateException(
                                  "timed out waiting for the test to release the userDataStream POST");
                            }
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                          }
                        }
                        return response;
                      }

                      @Override
                      public String getName() {
                        return "blocking-user-data-stream-post";
                      }
                    }));
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"created-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=created-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"created-key\"}")));

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

    TestObserver<Void> observer = exchange.connect().test();
    // The key-creation POST is guaranteed to be in flight here (the transformer is inside it).
    assertTrue(postStarted.await(10, TimeUnit.SECONDS));

    // Disconnect while the POST is in flight. The disconnect body is synchronized with the key
    // creation, so it blocks until the POST completes, then invalidates the attempt; it runs on
    // its own thread so the release below can proceed.
    AtomicReference<Throwable> disconnectFailure = new AtomicReference<>();
    Thread disconnectThread =
        new Thread(
            () -> {
              try {
                exchange.disconnect().blockingAwait();
              } catch (Throwable t) {
                disconnectFailure.set(t);
              }
            });
    disconnectThread.start();
    releasePost.countDown();
    disconnectThread.join(TimeUnit.SECONDS.toMillis(10));
    assertFalse(disconnectThread.isAlive(), "disconnect did not complete in time");
    assertNull(disconnectFailure.get());
    observer.awaitDone(10, TimeUnit.SECONDS);

    // No socket is open and the created key is closed: the disconnect left nothing behind even
    // though the transport did not exist when it ran.
    assertFalse(exchange.isAlive());
    assertNull(listenKeyInstance(exchange));
    assertNull(keepAliveDisposableInstance(exchange));
    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=created-key")));

    // The cancelled attempt was invalidated: a new connect starts a fresh attempt (new key and
    // transport) instead of sharing the dead one.
    Object cancelledService = streamingServiceInstance(exchange);
    exchange
        .connect()
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(IOException.class);
    assertNotSame(cancelledService, streamingServiceInstance(exchange));
    wireMock.verify(2, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));

    exchange.disconnect().onErrorComplete().blockingAwait();
  }

  @Test
  void disconnectClosesAnInFlightTransportConnectThatCompletesAfterwards() throws Exception {
    // Deterministic seam: a backlog-1 raw TCP server. A filler connection fills the accept
    // queue, so the client's SYN is dropped and its TCP connect stays pending; the disconnect
    // therefore runs while the transport connect is genuinely in progress (no channel is
    // assigned yet, so NettyStreamingService.disconnect() cannot cancel it). Releasing the
    // queue lets the client's retried SYN through, the WebSocket handshake completes, and
    // only the post-completion invalidation can tear the socket down afterwards.
    ServerSocket serverSocket = new ServerSocket(0, 1);
    CountDownLatch releaseQueue = new CountDownLatch(1);
    CountDownLatch tcpAccepted = new CountDownLatch(1);
    CountDownLatch releaseUpgrade = new CountDownLatch(1);
    CountDownLatch socketClosed = new CountDownLatch(1);
    Thread serverThread =
        new Thread(
            () -> {
              // Hold the queue full until the test's disconnect has run with the connect
              // still pending; accepting the filler frees the queue for the retried SYN.
              try {
                if (!releaseQueue.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException(
                      "timed out waiting for the test to release the accept queue");
                }
                Socket filler = serverSocket.accept();
                filler.close();
                try (Socket socket = serverSocket.accept()) {
                  tcpAccepted.countDown();
                  InputStream in = socket.getInputStream();
                  ByteArrayOutputStream request = new ByteArrayOutputStream();
                  byte[] buffer = new byte[1024];
                  while (!request.toString(StandardCharsets.UTF_8).contains("\r\n\r\n")) {
                    int n = in.read(buffer);
                    if (n < 0) {
                      return;
                    }
                    request.write(buffer, 0, n);
                  }
                  String key = null;
                  for (String line : request.toString(StandardCharsets.UTF_8).split("\r\n")) {
                    if (line.regionMatches(true, 0, "sec-websocket-key:", 0, 18)) {
                      key = line.substring(18).trim();
                    }
                  }
                  if (!releaseUpgrade.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                        "timed out waiting for the test to release the WebSocket upgrade");
                  }
                  if (key != null) {
                    String accept =
                        Base64.getEncoder()
                            .encodeToString(
                                MessageDigest.getInstance("SHA-1")
                                    .digest(
                                        (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                            .getBytes(StandardCharsets.UTF_8)));
                    OutputStream out = socket.getOutputStream();
                    out.write(
                        ("HTTP/1.1 101 Switching Protocols\r\n"
                                + "Upgrade: websocket\r\n"
                                + "Connection: Upgrade\r\n"
                                + "Sec-WebSocket-Accept: "
                                + accept
                                + "\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                  }
                  // EOF means the client closed the socket; the only close source is the
                  // post-completion teardown, so its arrival is the deterministic signal.
                  while (in.read(buffer) >= 0) {
                    // drain until the client closes
                  }
                  socketClosed.countDown();
                }
              } catch (Throwable t) {
                // leave the latches unsatisfied: the test's awaits fail and surface the failure
              } finally {
                try {
                  serverSocket.close();
                } catch (Exception ignored) {
                  // best-effort cleanup
                }
              }
            });
    serverThread.setDaemon(true);
    serverThread.start();

    // Fills the accept queue: while it stays full the client's SYN is dropped, so the
    // exchange's transport connect cannot complete.
    Socket filler = new Socket();
    filler.connect(new InetSocketAddress("127.0.0.1", serverSocket.getLocalPort()));

    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI,
        "ws://127.0.0.1:" + serverSocket.getLocalPort() + "/ws");
    exchange.applySpecification(spec);

    TestObserver<Void> observer = exchange.connect().test();
    // The transport connect is guaranteed in flight here: the queue is still full, so the SYN
    // is dropped and the retry cannot complete until the server accepts the filler.
    exchange.disconnect().blockingAwait(10, TimeUnit.SECONDS);
    releaseQueue.countDown();
    // The client's retried SYN lands once the queue frees; TCP is established only now, after
    // the disconnect already completed.
    assertTrue(tcpAccepted.await(10, TimeUnit.SECONDS));

    // The disconnect cannot cancel the in-progress connect; it must complete and let the
    // connect finish, then tear the socket down at the connect's asynchronous completion.
    releaseUpgrade.countDown();

    observer.awaitDone(10, TimeUnit.SECONDS).assertComplete();
    // Without the post-completion invalidation no close would ever be issued and the server
    // would drain forever, so this latch only opens when the connect's completion tore the
    // transport down.
    assertTrue(socketClosed.await(10, TimeUnit.SECONDS));
    assertFalse(exchange.isAlive());
    serverThread.join(TimeUnit.SECONDS.toMillis(10));
    filler.close();
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
