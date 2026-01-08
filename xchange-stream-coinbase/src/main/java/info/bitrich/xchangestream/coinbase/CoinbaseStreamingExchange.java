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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.knowm.xchange.instrument.Instrument;
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

  public static final String PARAM_PUBLIC_RATE_LIMIT = "Coinbase_Public_Subscriptions_Per_Second";
  public static final String PARAM_PRIVATE_RATE_LIMIT = "Coinbase_Private_Subscriptions_Per_Second";
  public static final String PARAM_MANUAL_HEARTBEAT = "Coinbase_Disable_Auto_Heartbeat";
  public static final String PARAM_DEFAULT_CANDLE_GRANULARITY =
      "Coinbase_Default_Candle_Granularity";
  public static final String PARAM_DEFAULT_CANDLE_PRODUCT_TYPE =
      "Coinbase_Default_Candle_Product_Type";
  public static final String PARAM_WEBSOCKET_JWT_SUPPLIER =
      "Coinbase_Websocket_Jwt_Supplier";

  // WebSocket endpoints for Coinbase Advanced Trade
  // Note: There is no sandbox environment for WebSocket connections
  public static final String MARKET_DATA_WS_URI = "wss://advanced-trade-ws.coinbase.com";
  public static final String USER_ORDER_DATA_WS_URI = "wss://advanced-trade-ws-user.coinbase.com";
  
  // Default to market data endpoint for backward compatibility
  public static final String PROD_WS_URI = MARKET_DATA_WS_URI;

  private final AtomicReference<Disposable> reconnectSubscription = new AtomicReference<>();
  private final List<Disposable> productSubscriptions = new CopyOnWriteArrayList<>();

  // Protected for testing - allows subclasses to inject mock services
  protected CoinbaseStreamingService streamingService;
  protected CoinbaseStreamingMarketDataService streamingMarketDataService;
  protected CoinbaseStreamingTradeService tradeService;

  @Override
  protected void initServices() {
    super.initServices();
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    ExchangeSpecification exchangeSpecification = getExchangeSpecification();
    
    // Create services using factory methods (can be overridden in tests)
    if (streamingService == null) {
      streamingService = createStreamingService(exchangeSpecification);
    }
    
    if (streamingMarketDataService == null) {
      streamingMarketDataService = createStreamingMarketDataService(exchangeSpecification);
    }
    
    if (tradeService == null) {
      tradeService = createStreamingTradeService(exchangeSpecification);
    }

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

    return connectCompletable
        .doOnComplete(this::autoSubscribeHeartbeatsIfConfigured)
        .doOnComplete(() -> processProductSubscriptions(args));
  }

  /**
   * Factory method to create the streaming service. Can be overridden in tests to inject mocks.
   *
   * @param exchangeSpecification The exchange specification
   * @return The streaming service instance
   */
  protected CoinbaseStreamingService createStreamingService(
      ExchangeSpecification exchangeSpecification) {
    String websocketUrl = resolveWebsocketUrl(exchangeSpecification);
    LOG.info("Creating CoinbaseStreamingService with WebSocket endpoint: {}", websocketUrl);

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

    LOG.info("Coinbase WebSocket rate limits - Public: {} per second, Private: {} per second", 
        publicRateLimit, privateRateLimit);

    CoinbaseStreamingService service =
        new CoinbaseStreamingService(
            websocketUrl,
            resolveJwtSupplier(exchangeSpecification),
            publicRateLimit,
            privateRateLimit);
    applyStreamingSpecification(exchangeSpecification, service);
    return service;
  }

  /**
   * Factory method to create the streaming market data service. Can be overridden in tests to
   * inject mocks.
   *
   * @param exchangeSpecification The exchange specification
   * @return The streaming market data service instance
   */
  protected CoinbaseStreamingMarketDataService createStreamingMarketDataService(
      ExchangeSpecification exchangeSpecification) {
    return new CoinbaseStreamingMarketDataService(
        streamingService, createSnapshotProvider(), exchangeSpecification);
  }

  /**
   * Factory method to create the streaming trade service. Can be overridden in tests to inject
   * mocks.
   *
   * @param exchangeSpecification The exchange specification
   * @return The streaming trade service instance
   */
  protected CoinbaseStreamingTradeService createStreamingTradeService(
      ExchangeSpecification exchangeSpecification) {
    return new CoinbaseStreamingTradeService(streamingService, exchangeSpecification);
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
    productSubscriptions.forEach(Disposable::dispose);
    productSubscriptions.clear();
    if (streamingService == null) {
      return Completable.complete();
    }
    CoinbaseStreamingService service = streamingService;
    resetServices();
    return service.disconnect();
  }

  /**
   * Reset service references. Package-private for testing.
   */
  void resetServices() {
    streamingService = null;
    streamingMarketDataService = null;
    tradeService = null;
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
    Supplier<String> injectedSupplier = extractInjectedJwtSupplier(specification);
    if (injectedSupplier != null) {
      return injectedSupplier;
    }

    Supplier<String> helperSupplier = attemptHelperJwtSupplier(specification);
    return helperSupplier != null ? helperSupplier : createLocalJwtSupplier(specification);
  }

  @SuppressWarnings("unchecked")
  private Supplier<String> extractInjectedJwtSupplier(ExchangeSpecification specification) {
    Object param =
        specification.getExchangeSpecificParametersItem(PARAM_WEBSOCKET_JWT_SUPPLIER);
    if (param == null) {
      return null;
    }
    if (param instanceof Supplier) {
      return (Supplier<String>) param;
    }
    LOG.warn(
        "Ignoring Coinbase websocket JWT supplier parameter of unsupported type: {}",
        param.getClass().getName());
    return null;
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
    try {
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
    } catch (IllegalStateException e) {
      // Handle case where keys are invalid (e.g., in tests with dummy keys)
      LOG.debug("Inline Coinbase JWT supplier unavailable - invalid API credentials: {}", e.getMessage());
      return () -> null;
    }
  }

  private String resolveWebsocketUrl(ExchangeSpecification specification) {
    String url;
    if (specification.getOverrideWebsocketApiUri() != null) {
      url = specification.getOverrideWebsocketApiUri();
      LOG.info("Using override WebSocket URI: {}", url);
    } else {
      url = PROD_WS_URI;
      LOG.info("Using default WebSocket URI: {} (market data endpoint)", url);
    }
    return url;
  }

  /**
   * Process ProductSubscription objects and create subscriptions. Package-private for testing.
   *
   * @param args The ProductSubscription objects to process
   */
  void processProductSubscriptions(ProductSubscription... args) {
    if (args == null || args.length == 0 || streamingMarketDataService == null) {
      return;
    }

    // Process all ProductSubscription objects
    for (ProductSubscription subscription : args) {
      if (subscription == null) {
        continue;
      }

      // Subscribe to tickers
      for (Instrument instrument : subscription.getTicker()) {
        if (instrument instanceof CurrencyPair) {
          CurrencyPair pair = (CurrencyPair) instrument;
          try {
            Disposable disposable =
                streamingMarketDataService
                    .getTicker(pair)
                    .subscribe(
                        ticker -> {
                          // No-op: subscription is active but data is consumed by explicit subscribers
                        },
                        error ->
                            LOG.debug(
                                "Error in ticker subscription for {}: {}", pair, error.getMessage()));
            productSubscriptions.add(disposable);
          } catch (Exception e) {
            LOG.warn("Failed to subscribe to ticker for {}", pair, e);
          }
        }
      }

      // Subscribe to trades
      for (Instrument instrument : subscription.getTrades()) {
        if (instrument instanceof CurrencyPair) {
          CurrencyPair pair = (CurrencyPair) instrument;
          try {
            Disposable disposable =
                streamingMarketDataService
                    .getTrades(pair)
                    .subscribe(
                        trade -> {
                          // No-op: subscription is active but data is consumed by explicit subscribers
                        },
                        error ->
                            LOG.debug(
                                "Error in trades subscription for {}: {}", pair, error.getMessage()));
            productSubscriptions.add(disposable);
          } catch (Exception e) {
            LOG.warn("Failed to subscribe to trades for {}", pair, e);
          }
        }
      }

      // Subscribe to order books
      for (Instrument instrument : subscription.getOrderBook()) {
        if (instrument instanceof CurrencyPair) {
          CurrencyPair pair = (CurrencyPair) instrument;
          try {
            Disposable disposable =
                streamingMarketDataService
                    .getOrderBook(pair)
                    .subscribe(
                        orderBook -> {
                          // No-op: subscription is active but data is consumed by explicit subscribers
                        },
                        error ->
                            LOG.debug(
                                "Error in order book subscription for {}: {}",
                                pair,
                                error.getMessage()));
            productSubscriptions.add(disposable);
          } catch (Exception e) {
            LOG.warn("Failed to subscribe to order book for {}", pair, e);
          }
        }
      }

      // Subscribe to user trades (requires authentication)
      if (tradeService != null) {
        for (Instrument instrument : subscription.getUserTrades()) {
          if (instrument instanceof CurrencyPair) {
            CurrencyPair pair = (CurrencyPair) instrument;
            try {
              Disposable disposable =
                  tradeService
                      .getUserTrades(pair)
                      .subscribe(
                          userTrade -> {
                            // No-op: subscription is active but data is consumed by explicit subscribers
                          },
                          error ->
                              LOG.debug(
                                  "Error in user trades subscription for {}: {}",
                                  pair,
                                  error.getMessage()));
              productSubscriptions.add(disposable);
            } catch (Exception e) {
              LOG.warn("Failed to subscribe to user trades for {}", pair, e);
            }
          }
        }

        // Subscribe to order changes (requires authentication)
        for (Instrument instrument : subscription.getOrders()) {
          if (instrument instanceof CurrencyPair) {
            CurrencyPair pair = (CurrencyPair) instrument;
            try {
              Disposable disposable =
                  tradeService
                      .getOrderChanges(pair)
                      .subscribe(
                          order -> {
                            // No-op: subscription is active but data is consumed by explicit subscribers
                          },
                          error ->
                              LOG.debug(
                                  "Error in order changes subscription for {}: {}",
                                  pair,
                                  error.getMessage()));
              productSubscriptions.add(disposable);
            } catch (Exception e) {
              LOG.warn("Failed to subscribe to order changes for {}", pair, e);
            }
          }
        }
      }
    }
  }
}
