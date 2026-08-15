package info.bitrich.xchangestream.mexc;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
  private Disposable keepAliveDisposable;

  @Override
  public Completable connect(ProductSubscription... args) {
    if (isAlive()) {
      return Completable.complete();
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
    final String resolvedUri = uri;
    if (specification.getApiKey() != null && !uri.contains("listenKey=")) {
      if (listenKey == null) {
        // Private connection: create a listen key, attach it to the URI, and keep it alive.
        return Completable.fromAction(() -> openPrivateConnection(resolvedUri))
            .subscribeOn(Schedulers.io())
            .andThen(Completable.defer(() -> streamingService.connect()));
      }
      // A previous attempt created a listen key and its keepalive is still running; reuse that
      // key instead of creating a second one. Creating a new key on every retry would orphan
      // the previous one until its 60-minute expiry and repeated connection failures could
      // accumulate keys up to MEXC's per-user limit. The key must NOT be closed when a connect
      // attempt fails: the base service keeps reconnecting with the URI it was built with, and
      // the keepalive keeps that key valid for those reconnects.
      buildStreamingService(withListenKey(resolvedUri, listenKey));
      return Completable.defer(() -> streamingService.connect());
    }
    buildStreamingService(uri);
    return streamingService.connect();
  }

  @Override
  public Completable disconnect() {
    if (streamingService == null) {
      return Completable.complete();
    }
    stopKeepAlive();
    Completable closeKey = Completable.complete();
    if (listenKey != null) {
      String key = listenKey;
      listenKey = null;
      closeKey =
          Completable.fromAction(() -> closeListenKey(key))
              .subscribeOn(Schedulers.io())
              .onErrorComplete();
    }
    return closeKey.andThen(streamingService.disconnect());
  }

  @Override
  public boolean isAlive() {
    return streamingService != null && streamingService.isSocketOpen();
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return streamingService == null
        ? Observable.empty()
        : streamingService.subscribeReconnectFailure();
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return streamingService == null
        ? Observable.empty()
        : streamingService.subscribeConnectionSuccess();
  }

  @Override
  public Observable<Object> disconnectObservable() {
    return streamingService == null
        ? Observable.empty()
        : streamingService.subscribeDisconnect();
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
    if (streamingService != null) {
      streamingService.useCompressedMessages(compressedMessages);
    }
  }

  private void openPrivateConnection(String uri) {
    try {
      MexcV3AccountService accountService = (MexcV3AccountService) getAccountService();
      listenKey = accountService.createListenKey().getListenKey();
      buildStreamingService(withListenKey(uri, listenKey));
      startKeepAlive(accountService);
    } catch (IOException e) {
      throw new ExchangeException(
          "Failed to open MEXC Spot v3 private stream: "
              + MexcV3Redactor.sanitize(e.getMessage()),
          e);
    }
  }

  private static String withListenKey(String uri, String key) {
    return uri + (uri.contains("?") ? "&" : "?") + "listenKey=" + key;
  }

  private void startKeepAlive(MexcV3AccountService accountService) {
    stopKeepAlive();
    keepAliveDisposable =
        Observable.interval(keepAliveIntervalSeconds, TimeUnit.SECONDS, keepAliveScheduler)
            .concatMapCompletable(
                tick ->
                    Completable.defer(
                            () ->
                                Completable.fromAction(() -> keepAliveListenKey(accountService))
                                    .subscribeOn(Schedulers.io()))
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
    streamingMarketDataService =
        new MexcV3StreamingMarketDataService(
            streamingService, (MexcV3MarketDataServiceRaw) getMarketDataService());
    streamingAccountService = new MexcV3StreamingAccountService(streamingService);
    streamingTradeService = new MexcV3StreamingTradeService(streamingService);
  }
}
