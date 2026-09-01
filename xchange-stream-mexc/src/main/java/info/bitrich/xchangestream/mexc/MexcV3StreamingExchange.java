package info.bitrich.xchangestream.mexc;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.mexc.v3.MexcV3Exchange;
import org.knowm.xchange.mexc.v3.config.MexcV3Configuration;
import org.knowm.xchange.mexc.v3.client.MexcV3Redactor;
import org.knowm.xchange.mexc.v3.service.MexcV3AccountService;
import org.knowm.xchange.mexc.v3.service.MexcV3MarketDataServiceRaw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streaming exchange for MEXC Spot v3.
 *
 * <p>Public market data streams connect to {@value #DEFAULT_WEBSOCKET_URI}. When the exchange
 * specification carries an API key, {@link #connect(ProductSubscription...)} first opens a
 * user-data stream via {@code POST /api/v3/userDataStream} and appends the listen key to the
 * WebSocket URI ({@code ?listenKey=...}) so private channels (account/orders/deals) are
 * authorized; the key is kept alive every 30 minutes ({@code PUT /api/v3/userDataStream}, keys
 * expire after 60 minutes), reused across connection retries, and closed on {@link
 * #disconnect()} ({@code DELETE /api/v3/userDataStream}).
 */
public class MexcV3StreamingExchange extends MexcV3Exchange implements StreamingExchange {

  private static final Logger LOG = LoggerFactory.getLogger(MexcV3StreamingExchange.class);

  /** MEXC Spot v3 public WebSocket endpoint. */
  public static final String DEFAULT_WEBSOCKET_URI = "wss://wbs-api.mexc.com/ws";
  /** Exchange-specific parameter key to override the WebSocket URI. */
  public static final String PARAM_WEBSOCKET_URI = "WebsocketUri";
  /** MEXC documents a 60-minute listen-key lifetime; refresh at half that to keep a full retry window. */
  private static final long KEEPALIVE_INTERVAL_SECONDS = 30L * 60L;
  /** Refresh attempts per tick; a transient PUT failure must not kill the refresh schedule. */
  static final int KEEPALIVE_ATTEMPTS = 3;
  /** Test seam: refresh cadence; package-private for keepalive lifecycle tests. */
  long keepAliveIntervalSeconds = KEEPALIVE_INTERVAL_SECONDS;
  /** Test seam: scheduler driving keepalive ticks; package-private for deterministic lifecycle tests. */
  Scheduler keepAliveScheduler = Schedulers.computation();

  private MexcV3StreamingService streamingService;
  private MexcV3StreamingMarketDataService streamingMarketDataService;
  private MexcV3StreamingAccountService streamingAccountService;
  private MexcV3StreamingTradeService streamingTradeService;
  private String listenKey;
  /** True while {@link #listenKey} was adopted from the configured URI instead of created. */
  private volatile boolean suppliedListenKey;
  /**
   * Supplied listen key this exchange has already closed. A later connect() reads the unchanged
   * configured URI, which still carries that key; re-adopting it would keepalive and subscribe
   * with a key this exchange already deleted server-side. A URI key equal to this value is
   * treated as stale and replaced by a freshly created key.
   */
  private volatile String closedSuppliedKey;
  private Disposable keepAliveDisposable;
  /** Shared connection attempt while it is in flight; cleared when it settles. */
  private Completable inFlightConnect;
  /**
   * Incremented every time a disconnect executes. Connection attempts capture the generation
   * when they are created and abort once it moves: an attempt that has not yet built its
   * transport must not open a socket or create a listen key after the disconnect completed.
   */
  private volatile long connectGeneration;
  /**
   * Lifecycle events of every transport this exchange creates, forwarded from each new service.
   * Subscribers that start observing before the transport exists (the transport is built
   * lazily on connect) must still see the events of the first connection; returning the current
   * service's stream directly would hand them an immediately-completing empty observable.
   */
  private final Subject<Throwable> reconnectFailureRelay = PublishSubject.create();
  private final Subject<Object> connectionSuccessRelay = PublishSubject.create();
  private final Subject<Object> disconnectRelay = PublishSubject.create();
  /**
   * Compression preference for the WebSocket transport. Stored on the exchange because the
   * transport is built lazily on connect: the only reliable time to configure the initial
   * pipeline is before the transport exists, and a live transport is updated directly as well.
   */
  private boolean compressedMessages;

  @Override
  public Completable connect(ProductSubscription... args) {
    // Cold Completable factory: nothing is built, replaced, or opened until the returned
    // Completable is subscribed. Constructing (or composing) connection Completables before
    // subscribing any of them must not tear down a transport: buildStreamingService disconnects
    // the current service, so an eager build would cancel an in-flight or live connection owned
    // by a sibling chain, orphaning its socket.
    return Completable.defer(this::connectNow);
  }

  private synchronized Completable connectNow() {
    if (isAlive()) {
      return Completable.complete();
    }
    if (inFlightConnect != null) {
      // A sibling subscription already started a connection attempt that has not settled; share
      // that attempt instead of building a second transport. Building again now would disconnect
      // the in-flight service and leave the first caller connected to an orphaned one.
      return inFlightConnect;
    }
    ExchangeSpecification specification = getExchangeSpecification();
    String uri = getConfiguration().getStreamBaseUrl();
    if (specification.getExchangeSpecificParametersItem(MexcV3Configuration.STREAM_BASE_URL_KEY)
        == null) {
      String legacyUri =
          (String) specification.getExchangeSpecificParametersItem(PARAM_WEBSOCKET_URI);
      if (legacyUri != null && !legacyUri.isBlank()) {
        uri = legacyUri;
      }
    }
    // An empty listenKey parameter is not a key: adopting it into the refresh/cleanup lifecycle
    // would leave nothing to refresh or close. Drop the empty parameter and fall through to the
    // private path, which creates a key.
    uri = withoutEmptyListenKey(uri);
    final String resolvedUri = uri;
    final long generation = connectGeneration;
    String suppliedKeyInUri = listenKeyIn(uri);
    boolean staleSuppliedKey =
        suppliedKeyInUri != null && suppliedKeyInUri.equals(closedSuppliedKey);
    Completable attempt;
    if (specification.getApiKey() != null
        && (!uri.contains("listenKey=") || staleSuppliedKey)) {
      // The state check runs when the returned Completable is subscribed, not when connect() is
      // called: subscribing the same chain twice must not create a second listen key (the first
      // would be orphaned until its 60-minute expiry).
      // Holder for the resources this attempt creates: the deferred release runs after a
      // disconnect that could not see the transport, and by then a replacement connect may
      // have installed a new key, keepalive, and transport. The release must tear down only
      // what this attempt created, so openPrivateConnection captures its own resources into
      // this holder under the lock (the lock also publishes the write to the release).
      PrivateConnectionAttempt[] created = new PrivateConnectionAttempt[1];
      attempt =
          Completable.defer(
              () -> {
                if (listenKey == null) {
                  // Private connection: create a listen key, attach it to the URI, and keep it
                  // alive.
                  return Completable.defer(
                          () -> {
                            if (generation != connectGeneration) {
                              // A disconnect executed before this chain reached the key
                              // creation; it already reported complete, so nothing may be
                              // created or opened after it.
                              return Completable.complete();
                            }
                            return Completable.fromAction(
                                    () -> openPrivateConnection(resolvedUri, created))
                                .subscribeOn(Schedulers.io());
                          })
                      .andThen(
                          Completable.defer(
                              () -> {
                                if (generation != connectGeneration) {
                                  // A disconnect executed while the key was being created or the
                                  // transport built. The disconnect could not see the transport
                                  // and already reported complete, so release the private
                                  // connection here instead of opening a socket after it.
                                  return Completable.fromAction(
                                          () -> releasePrivateConnection(created[0]))
                                      .subscribeOn(Schedulers.io())
                                      .onErrorComplete();
                                }
                                MexcV3StreamingService service = streamingService;
                                return connectAndInvalidateAfterDisconnect(
                                    service.connect(), generation, service);
                              }));
                }
                // A previous attempt created a listen key and its keepalive is still running;
                // reuse that key instead of creating a second one. Creating a new key on every
                // retry would orphan the previous one until its 60-minute expiry and repeated
                // connection failures could accumulate keys up to MEXC's per-user limit. The key
                // must NOT be closed when a connect attempt fails: the base service keeps
                // reconnecting with the URI it was built with, and the keepalive keeps that key
                // valid for those reconnects.
                return Completable.defer(
                    () -> {
                      synchronized (MexcV3StreamingExchange.this) {
                        if (generation != connectGeneration) {
                          // A disconnect executed while this chain waited to build; it already
                          // reported complete, so no socket may open after it.
                          return Completable.complete();
                        }
                        if (isAlive()) {
                          // The first subscription of this chain already connected; subscribing
                          // the chain again must not rebuild the service, because
                          // buildStreamingService disconnects the current transport and would
                          // tear down the active streams.
                          return Completable.complete();
                        }
                        buildStreamingService(withListenKey(resolvedUri, listenKey));
                        MexcV3StreamingService service = streamingService;
                        return connectAndInvalidateAfterDisconnect(
                            service.connect(), generation, service);
                      }
                    });
              });
    } else if (specification.getApiKey() != null) {
      // The configured URI already carries a listen key. Adopt it into the refresh and cleanup
      // lifecycle instead of treating the connection as public: without a keepalive the key
      // expires after 60 minutes and private streams stop, and disconnect() could not close it.
      final String suppliedKey = suppliedKeyInUri;
      attempt =
          Completable.defer(
              () -> {
                synchronized (MexcV3StreamingExchange.this) {
                  if (generation != connectGeneration) {
                    // A disconnect executed while this chain waited to build; it already
                    // reported complete, so no socket may open after it.
                    return Completable.complete();
                  }
                  if (isAlive()) {
                    return Completable.complete();
                  }
                  if (listenKey == null) {
                    listenKey = suppliedKey;
                    suppliedListenKey = true;
                    buildStreamingService(resolvedUri);
                    startKeepAlive((MexcV3AccountService) getAccountService());
                  } else {
                    buildStreamingService(resolvedUri);
                  }
                  MexcV3StreamingService service = streamingService;
                  return connectAndInvalidateAfterDisconnect(
                      service.connect(), generation, service);
                }
              });
    } else {
      buildStreamingService(uri);
      MexcV3StreamingService service = streamingService;
      attempt = connectAndInvalidateAfterDisconnect(service.connect(), generation, service);
    }
    // cache() makes the attempt single-flight: the first subscription executes it and every
    // concurrent subscription shares the same result instead of building a second transport.
    // The cleanup is attached to the source BEFORE caching so it fires exactly once when the
    // cached attempt settles, independent of any individual observer: a subscriber that
    // disposes before the attempt finishes would skip a per-subscriber callback, leaving the
    // slot occupied and replaying the cached terminal result to every later connect() call.
    // The holder captures this call's exact cached instance for the identity guard: a newer
    // connect() that replaced the field must not be erased by a stale attempt's settle.
    Completable[] slot = new Completable[1];
    Completable shared = attempt.doOnTerminate(() -> clearInFlightConnect(slot[0])).cache();
    slot[0] = shared;
    inFlightConnect = shared;
    return shared;
  }

  private synchronized void clearInFlightConnect(Completable attempt) {
    // The shared attempt fires onTerminate once per subscriber; only clear the field when the
    // settled attempt is still the one cached there. An error handler could have started and
    // cached a replacement attempt before a later subscriber's callback runs; clearing
    // unconditionally would erase that replacement and let another caller start a competing
    // transport instead of joining it.
    if (inFlightConnect == attempt) {
      inFlightConnect = null;
    }
  }

  /**
   * Connects the service and tears the transport down if a disconnect executed while the connect
   * was in flight.
   *
   * <p>{@link NettyStreamingService#disconnect()} cannot cancel an in-progress connect: before
   * the TCP connect completes the channel is not assigned yet, so the delegated disconnect
   * reports completion without cancelling anything, and the connect listener then assigns the
   * channel and finishes the handshake — leaving the exchange alive after the disconnect
   * completed. The in-flight operation is therefore invalidated at its asynchronous completion:
   * once the connect settles (its channel is assigned by then), a moved generation tears the
   * transport down immediately.
   *
   * <p>The service is captured at wrap time: by the time the attempt settles, a newer connect
   * may have replaced {@link #streamingService}, and tearing down the attempt must touch only
   * the transport it owns.
   */
  private Completable connectAndInvalidateAfterDisconnect(
      Completable connect, long generation, MexcV3StreamingService service) {
    return connect.doOnTerminate(
        () -> {
          if (generation != connectGeneration && service != null && service.isSocketOpen()) {
            service.disconnect().onErrorComplete().subscribe();
          }
        });
  }

  /** Extracts the {@code listenKey} query parameter from a stream URI, or {@code null}. */
  private static String listenKeyIn(String uri) {
    int keyIndex = uri.indexOf("listenKey=");
    if (keyIndex < 0) {
      return null;
    }
    int valueStart = keyIndex + "listenKey=".length();
    int valueEnd = uri.indexOf('&', valueStart);
    String key = valueEnd < 0 ? uri.substring(valueStart) : uri.substring(valueStart, valueEnd);
    return key.isEmpty() ? null : key;
  }

  @Override
  public Completable disconnect() {
    // Cold Completable factory: the keepalive and listen-key state are only touched when the
    // returned Completable actually executes. An abandoned disconnect must not stop the
    // keepalive or discard the key reference while the socket stays open — the key would expire
    // 60 minutes later and no later disconnect could close it.
    return Completable.defer(
        () -> {
          MexcV3StreamingService service;
          String keyToClose;
          Disposable keepAlive;
          synchronized (this) {
            // Invalidate any in-flight attempt before reporting complete: an attempt that has not
            // built its transport yet would otherwise create a listen key or open a socket
            // after the disconnect finished. The attempt chains capture the generation and
            // abort once it moves; an attempt already past its last check is torn down here
            // because streamingService is visible under this lock.
            inFlightConnect = null;
            connectGeneration++;
            // Capture the transport, keepalive, and key state under the lock: a connect() that
            // lands after the generation moved may install a replacement service and key, and
            // this disconnect must tear down only the connection it captured — never the
            // replacement the newer connect is using.
            service = streamingService;
            keepAlive = keepAliveDisposable;
            keepAliveDisposable = null;
            if (listenKey != null) {
              keyToClose = listenKey;
              if (suppliedListenKey) {
                // The key was adopted from the configured URI, which still carries it. A later
                // connect() must not re-adopt this closed key: private subscriptions and the
                // keepalive would use a key this exchange already deleted server-side.
                closedSuppliedKey = listenKey;
              }
              suppliedListenKey = false;
              listenKey = null;
            } else {
              keyToClose = null;
            }
          }
          if (keepAlive != null) {
            keepAlive.dispose();
          }
          Completable closeKey = Completable.complete();
          if (keyToClose != null) {
            String key = keyToClose;
            closeKey =
                Completable.fromAction(() -> closeListenKey(key))
                    .subscribeOn(Schedulers.io())
                    .onErrorComplete();
          }
          if (service == null) {
            return closeKey;
          }
          return closeKey.andThen(service.disconnect());
        });
  }

  @Override
  public boolean isAlive() {
    // A transport whose TCP connection is up but whose WebSocket upgrade has not completed (or
    // has failed) must not count as alive: the socket check alone is true from the moment TCP
    // establishes, before the 101 settles the handshake. connect() reports success only for a
    // real handshake, so an isAlive() that is true during the pending-upgrade window would
    // hand concurrent connect() callers a premature complete() and let them treat a doomed
    // transport as connected.
    return streamingService != null
        && streamingService.isSocketOpen()
        && streamingService.isConnectionEstablished();
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return reconnectFailureRelay.share();
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return connectionSuccessRelay.share();
  }

  @Override
  public Observable<Object> disconnectObservable() {
    return disconnectRelay.share();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public StreamingAccountService getStreamingAccountService() {
    requirePrivateStream();
    return streamingAccountService;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    requirePrivateStream();
    return streamingTradeService;
  }

  /**
   * Private streams require an API key; without one no listen key can ever be attached and the
   * user-data channels would silently receive nothing on the public socket.
   */
  private void requirePrivateStream() {
    if (getExchangeSpecification().getApiKey() == null) {
      throw new ExchangeSecurityException(
          "MEXC Spot v3 private streams require an API key; configure the exchange "
              + "specification before connecting");
    }
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    // Store the preference so it survives until transport creation: the transport is built
    // lazily on connect, and the only reliable time to configure the initial WebSocket
    // pipeline is before the transport exists. A live transport is updated directly as well.
    this.compressedMessages = compressedMessages;
    if (streamingService != null) {
      streamingService.useCompressedMessages(compressedMessages);
    }
  }

  private synchronized void openPrivateConnection(
      String uri, PrivateConnectionAttempt[] created) {
    if (listenKey != null) {
      if (streamingService != null && streamingService.isSocketOpen()) {
        // A concurrent subscription of the same connect chain already connected; keep its
        // transport instead of rebuilding (rebuilding disconnects active streams).
        return;
      }
      // A concurrent subscription of the same connect chain already created the key; reuse it
      // instead of orphaning it.
      buildStreamingService(withListenKey(uri, listenKey));
      return;
    }
    try {
      MexcV3AccountService accountService = (MexcV3AccountService) getAccountService();
      listenKey = accountService.createListenKey().getListenKey();
      suppliedListenKey = false;
      buildStreamingService(withListenKey(uri, listenKey));
      startKeepAlive(accountService);
      // Capture the resources this attempt created before releasing the lock: a disconnect may
      // land between the key creation and the chain's next check, and by the time the release
      // runs a replacement connect may have installed a new key, keepalive, and transport. The
      // release reads this capture instead of the mutable fields so it tears down exactly this
      // attempt's connection.
      created[0] = new PrivateConnectionAttempt(listenKey, streamingService, keepAliveDisposable);
    } catch (IOException e) {
      throw new ExchangeException(
          "Failed to open MEXC Spot v3 private stream: "
              + MexcV3Redactor.sanitize(e.getMessage()),
          e);
    }
  }

  private static String withListenKey(String uri, String key) {
    // Replace an existing listenKey parameter instead of appending a second one: a stale
    // supplied key in the configured URI must not survive next to the freshly created key
    // (the exchange would take the stale value).
    int keyIndex = uri.indexOf("listenKey=");
    if (keyIndex >= 0) {
      int valueEnd = uri.indexOf('&', keyIndex + "listenKey=".length());
      String head = uri.substring(0, keyIndex);
      String tail = valueEnd < 0 ? "" : uri.substring(valueEnd);
      return head + "listenKey=" + key + tail;
    }
    return uri + (uri.contains("?") ? "&" : "?") + "listenKey=" + key;
  }

  /**
   * Removes a {@code listenKey=} query parameter whose value is empty, if present. An empty
   * value must not be adopted as a supplied key: {@link #listenKeyIn} reads it as {@code null},
   * so nothing would ever be refreshed or closed and the socket would connect without
   * authorization. The empty parameter is dropped and the caller falls through to the private
   * path, which creates a key.
   */
  private static String withoutEmptyListenKey(String uri) {
    if (uri.endsWith("?listenKey=")) {
      return uri.substring(0, uri.length() - "?listenKey=".length());
    }
    if (uri.endsWith("&listenKey=")) {
      return uri.substring(0, uri.length() - "&listenKey=".length());
    }
    if (uri.contains("?listenKey=&")) {
      return uri.replace("?listenKey=&", "?");
    }
    if (uri.contains("&listenKey=&")) {
      return uri.replace("&listenKey=&", "&");
    }
    return uri;
  }

  /**
   * Resources created by one private connection attempt: the listen key it created, the
   * transport built for it, and the keepalive refreshing its key. Captured under the lock at
   * the end of {@link #openPrivateConnection(String, PrivateConnectionAttempt[])} so a stale
   * attempt's release can tear down exactly the connection it created — never the mutable
   * fields, which a replacement connect may have already replaced.
   */
  private static final class PrivateConnectionAttempt {
    final String key;
    final MexcV3StreamingService service;
    final Disposable keepAlive;

    PrivateConnectionAttempt(String key, MexcV3StreamingService service, Disposable keepAlive) {
      this.key = key;
      this.service = service;
      this.keepAlive = keepAlive;
    }
  }

  /**
   * Releases the private connection created by an invalidated connect attempt: a disconnect
   * that landed while the attempt was creating its key could not see the transport (it did not
   * exist yet) and already reported complete, so the key, keepalive, and transport must still
   * be released here or the key would be orphaned until its 60-minute expiry. No socket was
   * opened for this connection.
   *
   * <p>Only the resources captured at creation time are released. By the time this runs, a
   * replacement connect may have installed a new listen key, keepalive, and transport; reading
   * the mutable fields would tear down the replacement connection. The field clears are
   * identity-guarded so a replacement's state survives, and the captured keepalive dispose and
   * service disconnect are idempotent if a replacement (or a disconnect) already released them.
   *
   * @param attempt the resources the invalidated attempt created, or {@code null} if it created
   *     none (for example it reused the key of an earlier attempt)
   */
  private synchronized void releasePrivateConnection(PrivateConnectionAttempt attempt) {
    if (attempt == null) {
      return;
    }
    if (attempt.keepAlive != null) {
      attempt.keepAlive.dispose();
      if (keepAliveDisposable == attempt.keepAlive) {
        keepAliveDisposable = null;
      }
    }
    if (attempt.key != null && listenKey != null && listenKey.equals(attempt.key)) {
      // The captured key is still the one this exchange owns: close it and clear the reference.
      // A disconnect that landed after the key was created captured the key in the same
      // critical section that moved the generation, closed it, and cleared the field — closing
      // it again here would delete a key that is already gone (and a replacement key that
      // merely shares the name would be deleted out from under its keepalive). A key created
      // after the disconnect (the orphan case this release exists for) is still the current
      // one and is closed here.
      listenKey = null;
      closeListenKey(attempt.key);
    }
    if (attempt.service != null) {
      attempt.service.disconnect().onErrorComplete().subscribe();
    }
  }

  private void startKeepAlive(MexcV3AccountService accountService) {
    stopKeepAlive();
    keepAliveDisposable =
        Observable.interval(keepAliveIntervalSeconds, TimeUnit.SECONDS, keepAliveScheduler)
            .concatMapCompletable(
                tick ->
                    Completable.defer(
                            () -> keepAliveAttempt(accountService).subscribeOn(Schedulers.io()))
                        // A transient PUT failure must not terminate the schedule: retry the
                        // tick a bounded number of times, then swallow and let the next tick
                        // refresh the key. Without this, one failure stopped all later refreshes
                        // and the key expired 60 minutes later, taking the private stream down.
                        .retry(KEEPALIVE_ATTEMPTS - 1)
                        .onErrorComplete(
                            e -> {
                              LOG.warn(
                                  "MEXC Spot v3 listenKey keepalive failed after {} attempts; "
                                      + "the next refresh runs in {} seconds: {}",
                                  KEEPALIVE_ATTEMPTS,
                                  keepAliveIntervalSeconds,
                                  String.valueOf(e.getMessage()));
                              return true;
                            }))
            .subscribe(
                () -> {},
                e ->
                    LOG.warn(
                        "MEXC Spot v3 listenKey keepalive schedule stopped: {}",
                        String.valueOf(e.getMessage())));
  }

  /**
   * Runs one listen-key refresh without reporting a late failure after the subscriber disposes.
   *
   * <p>Disposal before execution skips the HTTP request. Disposal during an in-flight request
   * allows the request to finish but prevents its eventual failure from escaping through RxJava's
   * global error handler.
   */
  Completable keepAliveAttempt(MexcV3AccountService accountService) {
    return Completable.create(
        emitter -> {
          if (emitter.isDisposed()) {
            return;
          }
          try {
            keepAliveListenKey(accountService);
            emitter.onComplete();
          } catch (Throwable error) {
            Exceptions.throwIfFatal(error);
            emitter.tryOnError(error);
          }
        });
  }

  private void keepAliveListenKey(MexcV3AccountService accountService) throws IOException {
    try {
      accountService.keepAliveListenKey(listenKey);
    } catch (IOException e) {
      throw new ExchangeException(
          "MEXC Spot v3 listenKey keepalive failed: "
              + MexcV3Redactor.sanitize(e.getMessage()),
          e);
    }
  }

  private void closeListenKey(String key) {
    try {
      ((MexcV3AccountService) getAccountService()).closeListenKey(key);
    } catch (IOException e) {
      LOG.debug(
          "MEXC Spot v3 listenKey close failed; the key expires in 60 minutes anyway: {}",
          MexcV3Redactor.sanitize(e.getMessage()));
    }
  }

  private void stopKeepAlive() {
    if (keepAliveDisposable != null) {
      keepAliveDisposable.dispose();
      keepAliveDisposable = null;
    }
  }

  private void buildStreamingService(String uri) {
    if (streamingService != null) {
      // Releasing a previous transport (e.g. a failed connect) before replacing it.
      streamingService.disconnect().onErrorComplete().subscribe();
    }
    streamingService = new MexcV3StreamingService(uri);
    applyStreamingSpecification(getExchangeSpecification(), streamingService);
    streamingService.useCompressedMessages(compressedMessages);
    // Forward this transport's lifecycle events into the exchange-level relays: subscribers
    // that started observing before the transport existed must still see the first connection.
    streamingService.subscribeReconnectFailure().subscribe(reconnectFailureRelay::onNext);
    streamingService.subscribeConnectionSuccess().subscribe(connectionSuccessRelay::onNext);
    streamingService.subscribeDisconnect().subscribe(disconnectRelay::onNext);
    streamingMarketDataService =
        new MexcV3StreamingMarketDataService(
            streamingService, (MexcV3MarketDataServiceRaw) getMarketDataService());
    streamingAccountService = new MexcV3StreamingAccountService(streamingService);
    streamingTradeService = new MexcV3StreamingTradeService(streamingService);
  }
}
