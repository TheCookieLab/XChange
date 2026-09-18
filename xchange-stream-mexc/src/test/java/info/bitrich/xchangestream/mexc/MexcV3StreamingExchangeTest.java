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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.mexc.v3.config.MexcV3Configuration;
import org.knowm.xchange.mexc.v3.service.MexcV3AccountService;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;

/** Streaming exchange wiring: default URI, connect lifecycle, and service accessors. */
class MexcV3StreamingExchangeTest {

  private WireMockServer wireMock;

  @AfterEach
  void tearDown() {
    RxJavaPlugins.setIoSchedulerHandler(null);
    RxJavaPlugins.setErrorHandler(null);
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
    forceConnected(serviceBefore);
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

  /**
   * Simulates a fully established transport: the socket is open AND the WebSocket handshake has
   * completed. The exchange treats a socket that is open but not yet upgraded as not alive (the
   * upgrade can still fail), so a transport simulation must convey both facts: an open socket
   * with a pending or failed handshake would otherwise take the not-alive path and rebuild,
   * which is not what a connected transport does.
   */
  private static void forceConnected(Object service) throws Exception {
    java.lang.reflect.Field channelField =
        NettyStreamingService.class.getDeclaredField("webSocketChannel");
    channelField.setAccessible(true);
    channelField.set(service, new io.netty.channel.embedded.EmbeddedChannel());
    java.lang.reflect.Field stateModelField =
        NettyStreamingService.class.getDeclaredField("connectionStateModel");
    stateModelField.setAccessible(true);
    Object stateModel = stateModelField.get(service);
    java.lang.reflect.Field stateField = stateModel.getClass().getDeclaredField("state");
    stateField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.concurrent.atomic.AtomicReference<info.bitrich.xchangestream.service.netty.ConnectionStateModel.State>
        state =
            (java.util.concurrent.atomic.AtomicReference<
                    info.bitrich.xchangestream.service.netty.ConnectionStateModel.State>)
                stateField.get(stateModel);
    state.set(info.bitrich.xchangestream.service.netty.ConnectionStateModel.State.OPEN);
  }

  private static boolean compressedMessagesInstance(Object service) throws Exception {
    java.lang.reflect.Field compressedField =
        NettyStreamingService.class.getDeclaredField("compressedMessages");
    compressedField.setAccessible(true);
    return compressedField.getBoolean(service);
  }

  @Test
  void keepaliveTransientFailureRetriesAndScheduleSurvives() throws Exception {
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
    // once the previous tick's work completed); a request listener counts each keepalive PUT
    // into the latch as it reaches WireMock, so the loop waits on the latch instead of polling
    // the request journal on a fixed delay.
    CountDownLatch keepalivePuts =
        new CountDownLatch(MexcV3StreamingExchange.KEEPALIVE_ATTEMPTS * 2);
    wireMock.addMockServiceRequestListener(
        (request, response) -> {
          if ("PUT".equals(request.getMethod().value())) {
            keepalivePuts.countDown();
          }
        });
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (keepalivePuts.getCount() > 0 && System.nanoTime() < deadline) {
      keepAliveTicks.advanceTimeBy(1, TimeUnit.SECONDS);
      // Bridges the inherently asynchronous real HTTP requests to WireMock; the latch is the
      // signal, and the bounded wait only covers the in-flight round trip.
      keepalivePuts.await(100, TimeUnit.MILLISECONDS);
    }
    assertTrue(
        keepalivePuts.getCount() == 0,
        "keepalive schedule must survive failing ticks, saw "
            + (MexcV3StreamingExchange.KEEPALIVE_ATTEMPTS * 2L - keepalivePuts.getCount())
            + " PUTs");

    exchange.disconnect().onErrorComplete().blockingAwait();

    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key")));
  }

  @Test
  void disposedKeepaliveFailureDoesNotEscapeToRxGlobalHandler() throws Exception {
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    MexcV3AccountService accountService = mock(MexcV3AccountService.class);
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch releaseRequest = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              requestStarted.countDown();
              assertTrue(releaseRequest.await(10, TimeUnit.SECONDS));
              throw new IOException("HTTP 500");
            })
        .when(accountService)
        .keepAliveListenKey(null);
    AtomicReference<Throwable> undeliverable = new AtomicReference<>();
    RxJavaPlugins.setErrorHandler(undeliverable::set);

    TestObserver<Void> observer = new TestObserver<>();
    Thread worker = new Thread(() -> exchange.keepAliveAttempt(accountService).subscribe(observer));
    worker.start();
    assertTrue(requestStarted.await(10, TimeUnit.SECONDS));

    observer.dispose();
    releaseRequest.countDown();
    worker.join(TimeUnit.SECONDS.toMillis(10));

    assertFalse(worker.isAlive(), "keepalive attempt did not finish");
    assertNull(undeliverable.get());
  }

  @Test
  void disposedKeepaliveAttemptDoesNotStartRequest() {
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    MexcV3AccountService accountService = mock(MexcV3AccountService.class);
    TestObserver<Void> observer = new TestObserver<>();
    observer.dispose();

    exchange.keepAliveAttempt(accountService).subscribe(observer);

    verifyNoInteractions(accountService);
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
    forceConnected(serviceAfterFirst);
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
    // The transport double's connect stays pending until the test settles it, so the attempt is
    // deterministically in flight (and isAlive() false) while both chains subscribe — no
    // unroutable address, connect timeout, or real socket involved.
    TransportDouble transport = new TransportDouble("ws://transport-double/ws");
    MexcV3StreamingExchange exchange = exchangeWithTransports(transport);

    Completable first = exchange.connect();
    first.test(); // builds the transport; the attempt stays in flight (connect pending)
    assertEquals(1, transport.connectCalls());
    assertFalse(exchange.isAlive());
    Object serviceAfterFirst = streamingServiceInstance(exchange);
    assertNotNull(serviceAfterFirst);

    // A second subscription while the first attempt is in flight must share it: building
    // again would disconnect the in-flight transport and leave the first caller connected
    // to an orphaned service.
    Completable second = exchange.connect();
    second.test();
    assertSame(serviceAfterFirst, streamingServiceInstance(exchange));
    assertEquals(
        1, transport.connectCalls(), "the second subscription must neither rebuild nor reconnect");

    // Cleanup: the pending attempt cannot be cancelled, so settle it and let the post-completion
    // invalidation tear its transport down.
    exchange.disconnect().onErrorComplete().blockingAwait();
    transport.connectOutcome.onComplete();
    assertFalse(exchange.isAlive());
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
  void disconnectOnlyTearsDownTheConnectionItCaptured() throws Exception {
    // Deterministic seam for the disconnect/replacement-connect race: the keepalive disposable
    // field is swapped for a fake whose first dispose() blocks until the test releases it. A
    // disconnect that has already bumped the generation then pauses inside its keepalive
    // cleanup — after its lock is released, before it reads the listen key and service — which
    // is exactly the window in which a concurrent connect() installs a replacement connection.
    // The replacement must not be torn down or have its key state cleared by the older
    // disconnect.
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlPathEqualTo("/api/v3/userDataStream"))
            .inScenario("createKey")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withBody("{\"listenKey\":\"K1\"}"))
            .willSetStateTo("k2"));
    wireMock.stubFor(
        post(urlPathEqualTo("/api/v3/userDataStream"))
            .inScenario("createKey")
            .whenScenarioStateIs("k2")
            .willReturn(aResponse().withBody("{\"listenKey\":\"K2\"}"))
            .willSetStateTo("k3"));
    wireMock.stubFor(
        post(urlPathEqualTo("/api/v3/userDataStream"))
            .inScenario("createKey")
            .whenScenarioStateIs("k3")
            .willReturn(aResponse().withBody("{\"listenKey\":\"K3\"}"))
            .willSetStateTo("k4"));
    wireMock.stubFor(
        post(urlPathEqualTo("/api/v3/userDataStream"))
            .inScenario("createKey")
            .whenScenarioStateIs("k4")
            .willReturn(aResponse().withBody("{\"listenKey\":\"K4\"}"))
            .willSetStateTo("k5"));
    wireMock.stubFor(
        delete(urlPathEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"closed\"}")));

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

    // Two failed private connections (K1, K2) establish the captured connection the disconnect
    // will target.
    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    exchange.disconnect().onErrorComplete().blockingAwait();
    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    assertEquals("K2", listenKeyInstance(exchange));

    // Swap in the blocking disposable: the next disconnect pauses inside its keepalive
    // cleanup after releasing the lock.
    CountDownLatch disposeEntered = new CountDownLatch(1);
    CountDownLatch releaseDispose = new CountDownLatch(1);
    AtomicBoolean firstDispose = new AtomicBoolean(true);
    Disposable blockingDisposable =
        new Disposable() {
          private volatile boolean disposed;

          @Override
          public void dispose() {
            // Only the first dispose blocks: the replacement connect's own startKeepAlive
            // calls stopKeepAlive() while the disconnect is paused and must not deadlock on
            // the same disposable.
            if (firstDispose.compareAndSet(true, false)) {
              disposeEntered.countDown();
              try {
                if (!releaseDispose.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException(
                      "timed out waiting to release the keepalive dispose");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
              }
            }
            disposed = true;
          }

          @Override
          public boolean isDisposed() {
            return disposed;
          }
        };
    Field disposableField = MexcV3StreamingExchange.class.getDeclaredField("keepAliveDisposable");
    disposableField.setAccessible(true);
    disposableField.set(exchange, blockingDisposable);

    // The disconnect runs on its own thread and is guaranteed to be paused inside the
    // keepalive dispose (disposeEntered) before the replacement connect starts.
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
    assertTrue(disposeEntered.await(10, TimeUnit.SECONDS));

    // Install a replacement connection (K3) while the older disconnect is paused. The
    // replacement's key, keepalive, and service all land inside the older disconnect's
    // post-lock window.
    CountDownLatch replacementInstalled = new CountDownLatch(1);
    Thread replacementThread =
        new Thread(
            () -> {
              exchange.connect().test().awaitDone(15, TimeUnit.SECONDS);
              replacementInstalled.countDown();
            });
    replacementThread.start();
    assertTrue(replacementInstalled.await(15, TimeUnit.SECONDS));

    releaseDispose.countDown();
    disconnectThread.join(TimeUnit.SECONDS.toMillis(10));
    assertFalse(disconnectThread.isAlive(), "disconnect did not complete in time");
    assertNull(disconnectFailure.get());

    // The older disconnect closed only the K2 connection it captured: the replacement's key
    // and keepalive are intact and K3 was never closed.
    assertEquals("K3", listenKeyInstance(exchange));
    assertNotNull(keepAliveDisposableInstance(exchange));
    wireMock.verify(1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K2")));
    wireMock.verify(0, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K3")));

    // The exchange still works end to end: a later disconnect closes the replacement's K3 key
    // normally.
    exchange.disconnect().onErrorComplete().blockingAwait();
    wireMock.verify(1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K3")));
    assertNull(listenKeyInstance(exchange));
    assertNull(keepAliveDisposableInstance(exchange));

    // And a fresh connection starts a new lifecycle instead of reusing the closed key.
    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    assertEquals("K4", listenKeyInstance(exchange));
    exchange.disconnect().onErrorComplete().blockingAwait();
    wireMock.verify(1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K4")));
  }

  @Test
  void stalePrivateCleanupReleasesOnlyTheAttemptThatCreatedIt() throws Exception {
    // Deterministic seam for the deferred-release/replacement-connect race: the IO scheduler
    // (which runs key creation and the deferred release) is gated, so the test dispatches each
    // io task in the exact order that reproduces the race. The first attempt passes its
    // generation check and queues its key creation while the exchange lock is held; the
    // disconnect then bumps the generation (it can see nothing: the attempt has not created
    // anything yet) and reports complete; the replacement attempt queues its own key creation
    // with the post-disconnect generation. The replacement's key creation runs first and
    // installs a fresh connection; the first attempt's key creation then finds that key and
    // reuses it; and the first attempt's deferred release — which fires because its generation
    // moved — runs last, after the replacement connection is fully installed. The release must
    // tear down only the connection the invalidated attempt created, never the replacement's
    // key, keepalive, or transport.
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlPathEqualTo("/api/v3/userDataStream"))
            .inScenario("createKey")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withBody("{\"listenKey\":\"K2\"}"))
            .willSetStateTo("k1"));
    wireMock.stubFor(
        post(urlPathEqualTo("/api/v3/userDataStream"))
            .inScenario("createKey")
            .whenScenarioStateIs("k1")
            .willReturn(aResponse().withBody("{\"listenKey\":\"K1\"}"))
            .willSetStateTo("kDone"));
    wireMock.stubFor(
        delete(urlPathEqualTo("/api/v3/userDataStream")).willReturn(aResponse().withBody("{}")));

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
    // Keepalive ticks must not fire during the test.
    exchange.keepAliveIntervalSeconds = 3600L;

    GatedScheduler io = new GatedScheduler();
    RxJavaPlugins.setIoSchedulerHandler(scheduler -> io);
    try {
      TestObserver<Void> first;
      TestObserver<Void> replacement;
      synchronized (exchange) {
        first = exchange.connect().test();
        exchange.disconnect().blockingAwait();
        replacement = exchange.connect().test();
      }

      // Replacement key creation first: it installs the fresh K2 connection. The first
      // attempt's key creation then finds K2 already installed and reuses it (no second key),
      // and its deferred release runs last, after the replacement connection is fully in
      // place.
      io.dispatchLast();
      io.dispatchFirst();
      io.dispatchFirst();

      replacement.awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
      first.awaitDone(10, TimeUnit.SECONDS).assertComplete();

      // The stale release touched only the invalidated attempt: the replacement's key,
      // keepalive, and transport are intact, and no key was closed.
      assertEquals("K2", listenKeyInstance(exchange));
      assertNotNull(keepAliveDisposableInstance(exchange));
      wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
      wireMock.verify(0, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K2")));

      // The exchange still works end to end: a later disconnect closes the replacement's K2
      // key normally.
      io.open();
      exchange.disconnect().onErrorComplete().blockingAwait();
      assertNull(listenKeyInstance(exchange));
      assertNull(keepAliveDisposableInstance(exchange));
      wireMock.verify(1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K2")));

      // And a fresh connection starts a new lifecycle instead of reusing the closed key.
      exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
      assertEquals("K1", listenKeyInstance(exchange));
      exchange.disconnect().onErrorComplete().blockingAwait();
      wireMock.verify(1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K1")));
    } finally {
      RxJavaPlugins.setIoSchedulerHandler(null);
    }
  }

  @Test
  void disconnectBetweenKeyCreationAndDeferredReleaseClosesTheKeyExactlyOnce() throws Exception {
    // Deterministic seam for the disconnect-lands-between-key-creation-and-release window: the
    // IO scheduler (which runs key creation and the deferred release) is gated, so the test
    // dispatches each io task in the exact order that reproduces it. The attempt passes its
    // generation check at subscription (before any io task runs) and queues its key creation;
    // the disconnect then bumps the generation while no key exists yet (it captures nothing and
    // reports complete); the queued key creation runs and installs the key; and the attempt's
    // deferred release — which fires because its generation moved — runs last. The release must
    // close the key it created exactly once: the disconnect closed nothing (no key existed when
    // it ran), so a release that skipped or duplicated the close would either orphan the key
    // until its 60-minute expiry or delete a key that is already gone.
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"K1\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=K1"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"K1\"}")));

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
    // Keepalive ticks must not fire during the test.
    exchange.keepAliveIntervalSeconds = 3600L;

    GatedScheduler io = new GatedScheduler();
    RxJavaPlugins.setIoSchedulerHandler(scheduler -> io);
    try {
      TestObserver<Void> first;
      synchronized (exchange) {
        // The attempt's generation check passes here (subscription time); only the key
        // creation is queued. The disconnect then bumps the generation: no key exists yet, so
        // it captures nothing, closes nothing, and reports complete.
        first = exchange.connect().test();
        exchange.disconnect().blockingAwait();
      }

      // Key creation installs K1 and captures the attempt's resources; the deferred release is
      // then queued because the generation moved while the key was being created.
      io.dispatchFirst();
      io.dispatchFirst();

      first.awaitDone(10, TimeUnit.SECONDS).assertComplete();

      // The release closed exactly the key it created, once: nothing else existed to close
      // (the disconnect saw no key), and the key must not be left to expire in 60 minutes.
      assertNull(listenKeyInstance(exchange));
      assertNull(keepAliveDisposableInstance(exchange));
      wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
      wireMock.verify(1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K1")));

      // The exchange still works end to end: a fresh connection starts a new lifecycle and a
      // later disconnect closes its key normally.
      io.open();
      exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
      assertEquals("K1", listenKeyInstance(exchange));
      exchange.disconnect().onErrorComplete().blockingAwait();
      assertNull(listenKeyInstance(exchange));
      assertNull(keepAliveDisposableInstance(exchange));
      wireMock.verify(2, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=K1")));
    } finally {
      RxJavaPlugins.setIoSchedulerHandler(null);
    }
  }

  @Test
  void disconnectClosesAnInFlightTransportConnectThatCompletesAfterwards() {
    // The disconnect runs while the transport connect is genuinely in progress: no channel is
    // assigned yet, so the delegated disconnect cannot cancel it. The connect therefore settles
    // afterwards, and only the post-completion invalidation can tear the transport down.
    TransportDouble transport = new TransportDouble("ws://transport-double/ws");
    MexcV3StreamingExchange exchange = exchangeWithTransports(transport);

    TestObserver<Void> observer = exchange.connect().test();
    exchange.disconnect().blockingAwait();

    assertTrue(transport.isSocketOpen(), "the pending disconnect cannot cancel the connect");
    transport.connectOutcome.onComplete();
    observer.assertComplete();

    assertFalse(
        transport.isSocketOpen(), "the post-completion invalidation must tear the transport down");
    assertFalse(exchange.isAlive());
  }

  @Test
  void preConnectLifecycleObserversObserveTheFirstConnection() {
    // Lifecycle observers subscribed before any connect (the transport is built lazily on
    // connect) must stay subscribed instead of receiving an immediately-completing empty
    // observable: they have to observe the events of the first connection.
    TransportDouble transport = new TransportDouble("ws://transport-double/ws");
    MexcV3StreamingExchange exchange = exchangeWithTransports(transport);

    TestObserver<Throwable> reconnectFailure = exchange.reconnectFailure().test();
    TestObserver<Object> connectionSuccess = exchange.connectionSuccess().test();
    TestObserver<Object> disconnect = exchange.disconnectObservable().test();
    // No transport exists yet; the observers must stay subscribed instead of completing.
    reconnectFailure.assertNotComplete();
    connectionSuccess.assertNotComplete();
    disconnect.assertNotComplete();

    TestObserver<Void> connect = exchange.connect().test();
    transport.connectOutcome.onComplete();
    connect.assertComplete();
    // The pre-connect subscriber observes the first connection.
    connectionSuccess.assertValueCount(1);

    exchange.disconnect().blockingAwait();
    disconnect.assertValueCount(1);
    assertFalse(exchange.isAlive());
  }

  @Test
  void reconnectFailureObserverReceivesAFailedConnect() throws Exception {
    // A reconnect-failure observer subscribed before the connect observes the failure of the
    // first connect attempt (the refused URI surfaces a ConnectException on the netty layer).
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    TestObserver<Throwable> reconnectFailure = exchange.reconnectFailure().test();
    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);

    reconnectFailure.awaitCount(1);
    assertTrue(reconnectFailure.values().get(0) instanceof IOException);
  }

  @Test
  void compressionPreferenceSurvivesUntilTransportCreation() throws Exception {
    // useCompressedMessages(true) before connect() — the only reliable time to configure the
    // initial WebSocket pipeline — must not be discarded: the transport is built lazily on
    // connect, so the preference has to be stored on the exchange and applied to the service
    // when it is created.
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlPathEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"K1\"}")));
    wireMock.stubFor(
        delete(urlPathEqualTo("/api/v3/userDataStream")).willReturn(aResponse().withBody("{}")));

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
    exchange.keepAliveIntervalSeconds = 3600L;

    // No transport exists yet: the preference must be retained until the connect builds one.
    exchange.useCompressedMessages(true);

    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);

    MexcV3StreamingService service = (MexcV3StreamingService) streamingServiceInstance(exchange);
    assertNotNull(service);
    assertTrue(compressedMessagesInstance(service));
    // A live transport is updated directly as well.
    exchange.useCompressedMessages(false);
    assertFalse(compressedMessagesInstance(service));
    exchange.disconnect().onErrorComplete().blockingAwait();
  }

  @Test
  void failedAttemptIsClearedSoARetryStartsFresh() {
    // The shared in-flight slot must be cleared when an attempt settles, so a later connect()
    // executes a fresh transport attempt instead of replaying the cached terminal failure.
    MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://127.0.0.1:1/ws");
    exchange.applySpecification(spec);

    TestObserver<Void> first = exchange.connect().test();
    first.awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);

    TestObserver<Void> retry = exchange.connect().test();
    retry.awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);

    // The cached wrapper replays the exact same Throwable instance; a fresh attempt produces a
    // new one, so instance inequality proves the retry did not reuse the settled attempt.
    AtomicReference<Throwable> firstError = new AtomicReference<>();
    AtomicReference<Throwable> retryError = new AtomicReference<>();
    first
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(t -> firstError.compareAndSet(null, t));
    retry
        .awaitDone(10, TimeUnit.SECONDS)
        .assertError(t -> retryError.compareAndSet(null, t));
    assertNotNull(firstError.get());
    assertNotSame(firstError.get(), retryError.get());
    assertFalse(exchange.isAlive());
  }

  @Test
  void settlingAttemptClearsTheSlotEvenWhenItsInitiatorDisposed() {
    // Reviewer scenario: the observer that initiated the connection disposes before the attempt
    // settles. cache() keeps the upstream attempt running and retains its terminal result, so
    // the slot must clear when the cached attempt itself settles: a per-subscriber callback
    // never runs for the disposed initiator, and every later connect() would replay the cached
    // failure instead of retrying.
    TransportDouble first = new TransportDouble("ws://first/ws");
    TransportDouble retry = new TransportDouble("ws://retry/ws");
    MexcV3StreamingExchange exchange = exchangeWithTransports(first, retry);

    TestObserver<Void> initiator = exchange.connect().test();
    // The attempt is in flight (the transport connect is pending); dispose the initiating
    // observer before the attempt settles.
    initiator.dispose();

    // A joining subscriber receives the settled error.
    AtomicReference<Throwable> settledError = new AtomicReference<>();
    exchange.connect().subscribe(() -> {}, settledError::set);
    first.connectOutcome.onError(new IOException("handshake failed"));
    assertNotNull(settledError.get());
    assertFalse(exchange.isAlive());

    // The settled attempt must have cleared the slot: the retry runs a fresh transport attempt
    // instead of replaying the cached failure.
    TestObserver<Void> retryObserver = exchange.connect().test();
    assertEquals(1, retry.connectCalls(), "the retry must connect a fresh transport");
    retry.connectOutcome.onError(new IOException("retry failed"));
    AtomicReference<Throwable> retryError = new AtomicReference<>();
    retryObserver.assertError(t -> retryError.compareAndSet(null, t));
    assertNotNull(retryError.get());
    assertNotSame(settledError.get(), retryError.get());
    assertFalse(exchange.isAlive());
  }

  @Test
  void joiningSubscriberWhileTheHandshakeIsPendingSharesTheAttempt() throws Exception {
    // The initiator's TCP connect has established but the handshake has not settled. The
    // transport must not count as alive in that window — the upgrade can still fail — so a
    // joining subscriber shares the in-flight attempt and settles only when the handshake
    // settles, instead of completing early on a transport that may be about to fail.
    TransportDouble transport = new TransportDouble("ws://transport-double/ws");
    MexcV3StreamingExchange exchange = exchangeWithTransports(transport);

    TestObserver<Void> initiator = exchange.connect().test();
    // The TCP transport is open but the upgrade is pending: not alive.
    assertTrue(transport.isSocketOpen());
    assertFalse(exchange.isAlive());
    // Dispose the initiating observer so only the joining subscriber remains on the attempt.
    initiator.dispose();

    AtomicBoolean joinedSettled = new AtomicBoolean();
    AtomicReference<Throwable> joinedError = new AtomicReference<>();
    exchange
        .connect()
        .subscribe(
            () -> joinedSettled.set(true),
            t -> {
              joinedError.set(t);
              joinedSettled.set(true);
            });

    // No completion signal exists before the handshake settles: an early settle could only come
    // from classifying the pending-handshake transport as alive.
    assertFalse(joinedSettled.get(), "joining subscriber completed before the handshake settled");
    assertEquals(1, transport.connectCalls(), "the joining subscriber must share the attempt");
    assertSame(transport, streamingServiceInstance(exchange));

    transport.connectOutcome.onComplete();
    assertTrue(joinedSettled.get());
    assertNull(joinedError.get());
    assertTrue(exchange.isAlive());

    // Cleanup: the explicit disconnect closes the live transport.
    exchange.disconnect().blockingAwait();
    assertFalse(transport.isSocketOpen());
  }

  @Test
  void reconnectingAfterDisconnectCreatesAFreshKeyForAClosedSuppliedKey() throws Exception {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(
        post(urlEqualTo("/api/v3/userDataStream"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"fresh-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"test-listen-key\"}")));
    wireMock.stubFor(
        delete(urlEqualTo("/api/v3/userDataStream?listenKey=fresh-key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"fresh-key\"}")));

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
        "ws://127.0.0.1:1/ws?listenKey=test-listen-key");
    exchange.applySpecification(spec);

    // The supplied key is adopted into the lifecycle without creating one.
    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    wireMock.verify(0, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
    assertEquals("test-listen-key", listenKeyInstance(exchange));

    // Disconnect closes the supplied key.
    exchange.disconnect().blockingAwait(10, TimeUnit.SECONDS);
    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=test-listen-key")));

    // Reconnecting must not re-adopt the key this exchange already closed: the configured URI
    // still carries it, but private streams and the keepalive would use a deleted key. A fresh
    // key is created and replaces the stale one in the URI.
    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
    assertEquals("fresh-key", listenKeyInstance(exchange));

    // The fresh key is owned by this exchange: disconnecting closes it, and a third connect
    // creates another fresh key instead of reusing the closed one.
    exchange.disconnect().blockingAwait(10, TimeUnit.SECONDS);
    wireMock.verify(
        1, deleteRequestedFor(urlEqualTo("/api/v3/userDataStream?listenKey=fresh-key")));
    exchange.connect().test().awaitDone(10, TimeUnit.SECONDS).assertError(IOException.class);
    wireMock.verify(2, postRequestedFor(urlEqualTo("/api/v3/userDataStream")));
    exchange.disconnect().onErrorComplete().blockingAwait();
  }

  @Test
  void staleAttemptTeardownTouchesOnlyItsOwnTransport() {
    // The first attempt's transport connect stays pending while a disconnect invalidates it; a
    // second attempt then builds a fresh transport that becomes the live connection. Only when
    // that is in place does the stale first attempt settle: its post-completion teardown must
    // close only the first attempt's own transport, never the newer connection.
    TransportDouble first = new TransportDouble("ws://first/ws");
    TransportDouble second = new TransportDouble("ws://second/ws");
    MexcV3StreamingExchange exchange = exchangeWithTransports(first, second);

    TestObserver<Void> firstAttempt = exchange.connect().test();
    // The disconnect cannot cancel the pending transport connect; it invalidates the attempt
    // and reports complete.
    exchange.disconnect().blockingAwait();

    // Second attempt on a fresh transport, still pending.
    TestObserver<Void> secondAttempt = exchange.connect().test();
    assertEquals(1, second.connectCalls(), "the second attempt must build and connect its own transport");

    // Complete the second attempt first: its transport is live when the stale first attempt
    // settles.
    second.connectOutcome.onComplete();
    secondAttempt.assertComplete();
    assertTrue(exchange.isAlive());

    int disconnectsBeforeStaleSettle = first.disconnectCalls();
    first.connectOutcome.onComplete();
    firstAttempt.assertComplete();

    // The stale attempt's own transport is torn down...
    assertEquals(
        disconnectsBeforeStaleSettle + 1,
        first.disconnectCalls(),
        "the stale attempt's teardown must close its own transport");
    assertFalse(first.isSocketOpen());
    // ...while the newer connection is untouched.
    assertEquals(0, second.disconnectCalls(), "the live replacement transport must not be touched");
    assertTrue(exchange.isAlive());

    // Cleanup: the explicit disconnect closes the live transport.
    exchange.disconnect().blockingAwait();
    assertFalse(second.isSocketOpen());
  }

  /**
   * Controllable transport double: no sockets, threads, waits, or timeouts. It models the
   * transport states the exchange's connection lifecycle keys on — a connect that stays pending
   * until the test settles it, an open socket whose WebSocket upgrade has not settled (not
   * alive), and a disconnect that cannot cancel a connect still in flight — so the code under
   * test cannot tell it apart from a real transport.
   */
  private static final class TransportDouble extends MexcV3StreamingService {

    private final CompletableSubject connectOutcome = CompletableSubject.create();
    private final Subject<Object> connectionSuccess = PublishSubject.create();
    private final Subject<Object> disconnectEvents = PublishSubject.create();
    private final Subject<Throwable> reconnectFailures = PublishSubject.create();
    private final AtomicInteger connectCalls = new AtomicInteger();
    private final AtomicInteger disconnectCalls = new AtomicInteger();
    private final AtomicBoolean socketOpen = new AtomicBoolean(true);
    private final AtomicBoolean connectionEstablished = new AtomicBoolean();

    TransportDouble(String uri) {
      super(uri);
    }

    @Override
    public Completable connect() {
      connectCalls.incrementAndGet();
      return connectOutcome
          .doOnComplete(
              () -> {
                // The handshake settled: the transport is a usable WebSocket from here on.
                connectionEstablished.set(true);
                connectionSuccess.onNext(new Object());
              })
          .doOnError(reconnectFailures::onNext);
    }

    @Override
    public Completable disconnect() {
      disconnectCalls.incrementAndGet();
      if (connectionEstablished.getAndSet(false)) {
        // The connect settled, so a channel is assigned and this disconnect closes it.
        socketOpen.set(false);
        disconnectEvents.onNext(new Object());
      }
      // A disconnect issued while the connect is still pending cannot cancel it: no channel is
      // assigned yet, so it reports completion and leaves the socket open.
      return Completable.complete();
    }

    @Override
    public boolean isSocketOpen() {
      return socketOpen.get();
    }

    @Override
    public boolean isConnectionEstablished() {
      return connectionEstablished.get();
    }

    @Override
    public Observable<Object> subscribeConnectionSuccess() {
      return connectionSuccess;
    }

    @Override
    public Observable<Object> subscribeDisconnect() {
      return disconnectEvents;
    }

    @Override
    public Observable<Throwable> subscribeReconnectFailure() {
      return reconnectFailures;
    }

    int connectCalls() {
      return connectCalls.get();
    }

    int disconnectCalls() {
      return disconnectCalls.get();
    }
  }

  /**
   * An exchange whose transports are the supplied doubles, consumed in the order they are built.
   * The double replaces the real netty transport, so these tests exercise the exchange's
   * connection lifecycle without sockets, threads, or waits.
   */
  private static MexcV3StreamingExchange exchangeWithTransports(
      MexcV3StreamingService... transports) {
    ArrayDeque<MexcV3StreamingService> remaining = new ArrayDeque<>();
    for (MexcV3StreamingService transport : transports) {
      remaining.addLast(transport);
    }
    MexcV3StreamingExchange exchange =
        new MexcV3StreamingExchange() {
          @Override
          MexcV3StreamingService createStreamingService(String uri) {
            MexcV3StreamingService transport = remaining.poll();
            if (transport == null) {
              throw new AssertionError("The test supplied no transport for the build of " + uri);
            }
            return transport;
          }
        };
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    spec.setExchangeSpecificParametersItem(
        MexcV3StreamingExchange.PARAM_WEBSOCKET_URI, "ws://transport-double/ws");
    exchange.applySpecification(spec);
    return exchange;
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

  /**
   * Test seam for {@code Schedulers.io()}: every task is queued while the gate is closed, and
   * dispatched by the test on the calling thread in a chosen order, so a race between deferred
   * io tasks is reproduced deterministically. Once {@link #open()} is called, tasks run
   * immediately on the scheduler thread.
   */
  private static final class GatedScheduler extends Scheduler {
    private final Object gate = new Object();
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private boolean open;

    @Override
    public Disposable scheduleDirect(Runnable run, long delay, TimeUnit unit) {
      if (delay != 0) {
        throw new AssertionError("Unexpected delayed IO task: " + delay + " " + unit);
      }
      return scheduleDirect(run);
    }

    @Override
    public Disposable scheduleDirect(Runnable run) {
      boolean runNow;
      synchronized (gate) {
        runNow = open;
        if (!open) {
          pending.addLast(run);
        }
      }
      if (runNow) {
        run.run();
      }
      return new Disposable() {
        @Override
        public void dispose() {
          // Tasks run synchronously on the dispatch thread and are never cancelled.
        }

        @Override
        public boolean isDisposed() {
          return false;
        }
      };
    }

    @Override
    public Worker createWorker() {
      throw new AssertionError("Unexpected IO worker creation");
    }

    /** Runs the first queued task on the calling thread. */
    void dispatchFirst() {
      Runnable run;
      synchronized (gate) {
        run = pending.pollFirst();
      }
      if (run != null) {
        run.run();
      }
    }

    /** Runs the last queued task on the calling thread. */
    void dispatchLast() {
      Runnable run;
      synchronized (gate) {
        run = pending.pollLast();
      }
      if (run != null) {
        run.run();
      }
    }

    /** Runs queued tasks immediately from now on. */
    void open() {
      Runnable run;
      synchronized (gate) {
        open = true;
        while ((run = pending.pollFirst()) != null) {
          run.run();
        }
      }
    }
  }
}
