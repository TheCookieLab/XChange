package info.bitrich.xchangestream.coinbase;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Digest;
import org.knowm.xchange.coinbase.v3.service.CoinbaseMarketDataService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streaming exchange implementation for Coinbase Advanced Trade WebSockets.
 */
public class CoinbaseStreamingExchange extends CoinbaseExchange implements StreamingExchange {
  private static final Logger LOG = LoggerFactory.getLogger(CoinbaseStreamingExchange.class);

  public static final String PARAM_SANDBOX = "Use_Sandbox_Websocket";
  public static final String PARAM_PUBLIC_RATE_LIMIT = "Coinbase_Public_Subscriptions_Per_Second";
  public static final String PARAM_PRIVATE_RATE_LIMIT = "Coinbase_Private_Subscriptions_Per_Second";
  public static final String PARAM_MANUAL_HEARTBEAT = "Coinbase_Disable_Auto_Heartbeat";
  public static final String PARAM_DEFAULT_CANDLE_GRANULARITY =
      "Coinbase_Default_Candle_Granularity";
  public static final String PARAM_DEFAULT_CANDLE_PRODUCT_TYPE =
      "Coinbase_Default_Candle_Product_Type";

  public static final String PROD_WS_URI = "wss://advanced-trade-ws.coinbase.com";
  public static final String SANDBOX_WS_URI = "wss://advanced-trade-ws.sandbox.coinbase.com";

  private final AtomicReference<Disposable> reconnectSubscription = new AtomicReference<>();

  private CoinbaseStreamingService streamingService;
  private CoinbaseStreamingMarketDataService streamingMarketDataService;
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
            resolveJwtSupplier(exchangeSpecification),
            publicRateLimit,
            privateRateLimit);
    applyStreamingSpecification(exchangeSpecification, streamingService);

    streamingMarketDataService =
        new CoinbaseStreamingMarketDataService(
            streamingService, createSnapshotProvider(), exchangeSpecification);
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
    streamingMarketDataService.ensureHeartbeatsSubscription();
  }

  private void resubscribeOpenStreams() {
    streamingMarketDataService.resubscribe();
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
    streamingMarketDataService = null;
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
    return streamingMarketDataService;
  }

  public Observable<CandleStick> getCandles(CurrencyPair currencyPair, Object... args) {
    if (streamingMarketDataService == null) {
      throw new IllegalStateException("Streaming market data service not initialized");
    }
    return streamingMarketDataService.getCandles(currencyPair, args);
  }

  public Observable<CandleStick> getCandles(
      CurrencyPair currencyPair, CoinbaseCandleSubscriptionParams params) {
    if (streamingMarketDataService == null) {
      throw new IllegalStateException("Streaming market data service not initialized");
    }
    return streamingMarketDataService.getCandles(currencyPair, params);
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

  private Supplier<String> resolveJwtSupplier(ExchangeSpecification specification) {
    Supplier<String> helperSupplier = attemptHelperJwtSupplier(specification);
    return helperSupplier != null ? helperSupplier : createLocalJwtSupplier(specification);
  }

  private CoinbaseStreamingMarketDataService.OrderBookSnapshotProvider createSnapshotProvider() {
    if (marketDataService instanceof CoinbaseMarketDataService) {
      CoinbaseMarketDataService coinbaseMarketDataService =
          (CoinbaseMarketDataService) marketDataService;
      return coinbaseMarketDataService::getOrderBook;
    }
    LOG.warn(
        "Coinbase market data service not available for snapshot recovery; falling back to streaming updates only");
    return currencyPair -> null;
  }

  @SuppressWarnings("unchecked")
  private Supplier<String> attemptHelperJwtSupplier(ExchangeSpecification specification) {
    try {
      Class<?> helperClass =
          Class.forName("org.knowm.xchange.coinbase.v3.service.CoinbaseWebsocketAuthentication");
      Method supplierMethod = helperClass.getMethod("websocketJwtSupplier", ExchangeSpecification.class);
      return (Supplier<String>) supplierMethod.invoke(null, specification);
    } catch (ClassNotFoundException e) {
      LOG.debug(
          "CoinbaseWebsocketAuthentication helper not found on classpath; falling back to inline JWT supplier");
      return null;
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      LOG.warn(
          "Failed to use CoinbaseWebsocketAuthentication helper; falling back to inline JWT supplier",
          e);
      return null;
    }
  }

  private Supplier<String> createLocalJwtSupplier(ExchangeSpecification specification) {
    CoinbaseV3Digest digest =
        CoinbaseV3Digest.createInstance(specification.getApiKey(), specification.getSecretKey());
    if (digest == null) {
      LOG.debug("Inline Coinbase JWT supplier unavailable - missing API credentials");
      return () -> null;
    }
    return () -> {
      try {
        return digest.generateWebsocketJwt();
      } catch (Exception ex) {
        LOG.warn("Inline Coinbase JWT supplier failed to generate token", ex);
        return null;
      }
    };
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
