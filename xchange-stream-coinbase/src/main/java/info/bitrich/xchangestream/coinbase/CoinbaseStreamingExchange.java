package info.bitrich.xchangestream.coinbase;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.service.CoinbaseWebsocketAuthentication;

/**
 * Streaming exchange implementation for Coinbase Advanced Trade WebSockets.
 */
public class CoinbaseStreamingExchange extends CoinbaseExchange implements StreamingExchange {

  public static final String PARAM_SANDBOX = "Use_Sandbox_Websocket";
  public static final String PARAM_PUBLIC_RATE_LIMIT = "Coinbase_Public_Subscriptions_Per_Second";
  public static final String PARAM_PRIVATE_RATE_LIMIT = "Coinbase_Private_Subscriptions_Per_Second";
  public static final String PARAM_MANUAL_HEARTBEAT = "Coinbase_Disable_Auto_Heartbeat";

  public static final String PROD_WS_URI = "wss://advanced-trade-ws.coinbase.com";
  public static final String SANDBOX_WS_URI = "wss://advanced-trade-ws.sandbox.coinbase.com";

  private final AtomicReference<Disposable> reconnectSubscription = new AtomicReference<>();

  private CoinbaseStreamingService streamingService;
  private CoinbaseStreamingMarketDataService marketDataService;
  private CoinbaseStreamingTradeService tradeService;

  @Override
  protected void initServices() {
    super.initServices();
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    ExchangeSpecification exchangeSpecification = getExchangeSpecification();
    String websocketUrl = resolveWebsocketUrl(exchangeSpecification);

    int publicRateLimit =
        Optional.ofNullable(
                exchangeSpecification.getExchangeSpecificParametersItem(PARAM_PUBLIC_RATE_LIMIT))
            .map(Object::toString)
            .map(Integer::parseInt)
            .orElse(8);

    int privateRateLimit =
        Optional.ofNullable(
                exchangeSpecification.getExchangeSpecificParametersItem(PARAM_PRIVATE_RATE_LIMIT))
            .map(Object::toString)
            .map(Integer::parseInt)
            .orElse(750);

    streamingService =
        new CoinbaseStreamingService(
            websocketUrl,
            CoinbaseWebsocketAuthentication.websocketJwtSupplier(exchangeSpecification),
            publicRateLimit,
            privateRateLimit);
    applyStreamingSpecification(exchangeSpecification, streamingService);

    marketDataService = new CoinbaseStreamingMarketDataService(streamingService, exchangeSpecification);
    tradeService = new CoinbaseStreamingTradeService(streamingService, exchangeSpecification);

    Completable connectCompletable = streamingService.connect();

    reconnectSubscription.updateAndGet(
        previous -> {
          if (previous != null && !previous.isDisposed()) {
            previous.dispose();
          }
          return streamingService
              .subscribeConnectionSuccess()
              .doOnNext(o -> resubscribeOpenStreams())
              .subscribe();
        });

    return connectCompletable.doOnComplete(this::autoSubscribeHeartbeatsIfConfigured);
  }

  private void autoSubscribeHeartbeatsIfConfigured() {
    Object disableAutoHeartbeat =
        getExchangeSpecification().getExchangeSpecificParametersItem(PARAM_MANUAL_HEARTBEAT);
    if (Boolean.TRUE.equals(disableAutoHeartbeat)) {
      return;
    }
    marketDataService.ensureHeartbeatsSubscription();
  }

  private void resubscribeOpenStreams() {
    marketDataService.resubscribe();
    tradeService.resubscribe();
  }

  @Override
  public Completable disconnect() {
    Optional.ofNullable(reconnectSubscription.getAndSet(null))
        .ifPresent(Disposable::dispose);
    if (streamingService == null) {
      return Completable.complete();
    }
    CoinbaseStreamingService service = streamingService;
    streamingService = null;
    marketDataService = null;
    tradeService = null;
    return service.disconnect();
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return streamingService == null ? Observable.empty() : streamingService.subscribeReconnectFailure();
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return streamingService == null ? Observable.empty() : streamingService.subscribeConnectionSuccess();
  }

  @Override
  public Observable<State> connectionStateObservable() {
    return streamingService == null ? Observable.empty() : streamingService.subscribeConnectionState();
  }

  @Override
  public Observable<Object> connectionIdle() {
    return streamingService == null ? Observable.empty() : streamingService.subscribeIdle();
  }

  @Override
  public CoinbaseStreamingMarketDataService getStreamingMarketDataService() {
    return marketDataService;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return tradeService;
  }

  @Override
  public boolean isAlive() {
    return streamingService != null && streamingService.isSocketOpen();
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (streamingService != null) {
      streamingService.useCompressedMessages(compressedMessages);
    }
  }

  private String resolveWebsocketUrl(ExchangeSpecification specification) {
    if (specification.getOverrideWebsocketApiUri() != null) {
      return specification.getOverrideWebsocketApiUri();
    }
    Object useSandbox = specification.getExchangeSpecificParametersItem(PARAM_SANDBOX);
    if (Boolean.TRUE.equals(useSandbox)) {
      return SANDBOX_WS_URI;
    }
    return PROD_WS_URI;
  }
}
