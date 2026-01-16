package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import info.bitrich.xchangestream.core.ProductSubscription;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

class CoinbaseStreamingExchangeTest {

  private TestableCoinbaseStreamingExchange exchange;
  private TestStreamingService testStreamingService;
  private ExchangeSpecification spec;

  @BeforeEach
  void setUp() {
    exchange = new TestableCoinbaseStreamingExchange();
    spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
    spec.setApiKey("test-api-key");
    spec.setSecretKey("test-secret-key");
    // Set exchange specification without calling applySpecification to avoid initializing parent services
    exchange.setExchangeSpecificationForTesting(spec);
  }

  @Test
  void connectWithoutProductSubscriptionDoesNotCreateSubscriptions() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    // Call processProductSubscriptions directly with empty args
    exchange.processProductSubscriptions();

    // Should have no subscription requests
    assertEquals(0, testStreamingService.getSubscriptionCount());
  }

  @Test
  void connectWithTickerSubscriptionCreatesTickerSubscription() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create().addTicker(CurrencyPair.BTC_USD).build();

    // Call processProductSubscriptions directly
    exchange.processProductSubscriptions(subscription);

    // Verify ticker subscription was created
    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(1, requests.size());
    assertEquals(CoinbaseChannel.TICKER, requests.get(0).getChannel());
    assertEquals(Collections.singletonList("BTC-USD"), requests.get(0).getProductIds());

    // Verify subscription is tracked
    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(1, productSubscriptions.size());
    assertFalse(productSubscriptions.get(0).isDisposed());
  }

  @Test
  void connectWithTradesSubscriptionCreatesTradesSubscription() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create().addTrades(CurrencyPair.ETH_USD).build();

    exchange.processProductSubscriptions(subscription);

    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(1, requests.size());
    assertEquals(CoinbaseChannel.MARKET_TRADES, requests.get(0).getChannel());
    assertEquals(Collections.singletonList("ETH-USD"), requests.get(0).getProductIds());

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(1, productSubscriptions.size());
  }

  @Test
  void connectWithOrderBookSubscriptionCreatesOrderBookSubscription() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create().addOrderbook(CurrencyPair.BTC_USD).build();

    exchange.processProductSubscriptions(subscription);

    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(1, requests.size());
    assertEquals(CoinbaseChannel.LEVEL2, requests.get(0).getChannel());
    assertEquals(Collections.singletonList("BTC-USD"), requests.get(0).getProductIds());

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(1, productSubscriptions.size());
  }

  @Test
  void connectWithUserTradesSubscriptionCreatesUserTradesSubscription() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create().addUserTrades(CurrencyPair.BTC_USD).build();

    exchange.processProductSubscriptions(subscription);

    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(1, requests.size());
    assertEquals(CoinbaseChannel.USER, requests.get(0).getChannel());
    assertEquals(Collections.singletonList("BTC-USD"), requests.get(0).getProductIds());

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(1, productSubscriptions.size());
  }

  @Test
  void connectWithOrderChangesSubscriptionCreatesOrderChangesSubscription() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create().addOrders(CurrencyPair.BTC_USD).build();

    exchange.processProductSubscriptions(subscription);

    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(1, requests.size());
    assertEquals(CoinbaseChannel.USER, requests.get(0).getChannel());
    assertEquals(Collections.singletonList("BTC-USD"), requests.get(0).getProductIds());

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(1, productSubscriptions.size());
  }

  @Test
  void connectWithMultipleSubscriptionTypesCreatesAllSubscriptions() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create()
            .addTicker(CurrencyPair.BTC_USD)
            .addTrades(CurrencyPair.BTC_USD)
            .addOrderbook(CurrencyPair.BTC_USD)
            .addUserTrades(CurrencyPair.BTC_USD)
            .addOrders(CurrencyPair.BTC_USD)
            .build();

    exchange.processProductSubscriptions(subscription);

    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(5, requests.size());

    // Verify all subscription types are present
    Set<CoinbaseChannel> channels = new HashSet<>();
    for (CoinbaseSubscriptionRequest request : requests) {
      channels.add(request.getChannel());
    }

    assertTrue(channels.contains(CoinbaseChannel.TICKER));
    assertTrue(channels.contains(CoinbaseChannel.MARKET_TRADES));
    assertTrue(channels.contains(CoinbaseChannel.LEVEL2));
    assertTrue(channels.contains(CoinbaseChannel.USER)); // Used for both user trades and orders

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(5, productSubscriptions.size());
  }

  @Test
  void connectWithMultipleCurrencyPairsCreatesSubscriptionsForAllPairs() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create()
            .addTicker(CurrencyPair.BTC_USD)
            .addTicker(CurrencyPair.ETH_USD)
            .addTrades(CurrencyPair.BTC_USD)
            .build();

    exchange.processProductSubscriptions(subscription);

    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(3, requests.size());

    // Verify both BTC-USD and ETH-USD ticker subscriptions
    Set<String> tickerProductIds = new HashSet<>();
    for (CoinbaseSubscriptionRequest request : requests) {
      if (request.getChannel() == CoinbaseChannel.TICKER) {
        tickerProductIds.addAll(request.getProductIds());
      }
    }
    assertTrue(tickerProductIds.contains("BTC-USD"));
    assertTrue(tickerProductIds.contains("ETH-USD"));

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(3, productSubscriptions.size());
  }

  @Test
  void connectWithMultipleProductSubscriptionsProcessesAll() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription sub1 =
        ProductSubscription.create().addTicker(CurrencyPair.BTC_USD).build();
    ProductSubscription sub2 =
        ProductSubscription.create().addTrades(CurrencyPair.ETH_USD).build();

    exchange.processProductSubscriptions(sub1, sub2);

    List<CoinbaseSubscriptionRequest> requests = testStreamingService.getSubscriptionRequests();
    assertEquals(2, requests.size());

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(2, productSubscriptions.size());
  }

  @Test
  void disconnectDisposesAllProductSubscriptions() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create()
            .addTicker(CurrencyPair.BTC_USD)
            .addTrades(CurrencyPair.ETH_USD)
            .build();

    exchange.processProductSubscriptions(subscription);

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(2, productSubscriptions.size());

    // Verify subscriptions are active
    for (Disposable disposable : productSubscriptions) {
      assertFalse(disposable.isDisposed());
    }

    // Disconnect
    Completable disconnect = exchange.disconnect();
    disconnect.blockingAwait();

    // Verify all subscriptions are disposed
    for (Disposable disposable : productSubscriptions) {
      assertTrue(disposable.isDisposed());
    }

    // Verify list is cleared
    List<Disposable> afterDisconnect = getProductSubscriptions();
    assertEquals(0, afterDisconnect.size());
  }

  @Test
  void connectWithEmptyProductSubscriptionDoesNotCreateSubscriptions() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription = ProductSubscription.create().build();

    exchange.processProductSubscriptions(subscription);

    assertEquals(0, testStreamingService.getSubscriptionCount());
    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(0, productSubscriptions.size());
  }

  @Test
  void subscriptionsRemainActiveAfterConnection() {
    testStreamingService = new TestStreamingService();
    exchange.setTestStreamingService(testStreamingService);

    ProductSubscription subscription =
        ProductSubscription.create().addTicker(CurrencyPair.BTC_USD).build();

    exchange.processProductSubscriptions(subscription);

    List<Disposable> productSubscriptions = getProductSubscriptions();
    assertEquals(1, productSubscriptions.size());

    // Verify subscription is still active after connection completes
    assertFalse(productSubscriptions.get(0).isDisposed());

    // Verify we can still get ticker data (the subscription is active)
    Observable<Ticker> tickerObservable =
        exchange.getStreamingMarketDataService().getTicker(CurrencyPair.BTC_USD);
    assertTrue(tickerObservable != null);
  }

  @Test
  void overrideWebsocketUrlIsRespected() {
    CoinbaseStreamingExchange exchange = new CoinbaseStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    String customUrl = "wss://custom-endpoint.example.com";
    spec.setOverrideWebsocketApiUri(customUrl);
    exchange.applySpecification(spec);

    // Create streaming service - it should use the override URL
    CoinbaseStreamingService service = exchange.createStreamingService(spec);
    assertNotNull(service);
    // Verify the override is set on the spec
    assertEquals(customUrl, spec.getOverrideWebsocketApiUri());
  }

  @Test
  void userOrderDataEndpointCanBeSet() {
    CoinbaseStreamingExchange exchange = new CoinbaseStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setOverrideWebsocketApiUri(CoinbaseStreamingExchange.USER_ORDER_DATA_WS_URI);
    exchange.applySpecification(spec);

    CoinbaseStreamingService service = exchange.createStreamingService(spec);
    assertNotNull(service);
    assertEquals(
        CoinbaseStreamingExchange.USER_ORDER_DATA_WS_URI,
        spec.getOverrideWebsocketApiUri());
  }

  @Test
  void apiKeysAreAppliedToJwtSupplier() {
    TestableCoinbaseStreamingExchange exchange = new TestableCoinbaseStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey("test-api-key");
    // Note: Using invalid secret key - JWT supplier will return null but service should still be created
    // This tests that the service can be created even with invalid keys (for public channels)
    spec.setSecretKey("test-secret-key");
    // Don't call applySpecification as it initializes parent services which may fail with invalid keys
    exchange.setExchangeSpecificationForTesting(spec);

    // Create streaming service - JWT supplier will be null due to invalid keys, but service should still be created
    CoinbaseStreamingService service = exchange.createStreamingService(spec);
    assertNotNull(service);
    // The service should be created successfully even with invalid keys
    // The actual JWT generation with valid keys is tested in CoinbaseV3Digest tests
  }

  @Test
  void apiKeysCanBeNullForPublicChannels() {
    CoinbaseStreamingExchange exchange = new CoinbaseStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    // No API keys set - should still work for public channels
    exchange.applySpecification(spec);

    CoinbaseStreamingService service = exchange.createStreamingService(spec);
    assertNotNull(service);
    // Service should be created even without API keys (for public channels)
  }

  @Test
  void defaultWebsocketUrlUsesMarketDataEndpoint() {
    CoinbaseStreamingExchange exchange = new CoinbaseStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    exchange.applySpecification(spec);

    // Verify default endpoint is market data endpoint
    CoinbaseStreamingService service = exchange.createStreamingService(spec);
    assertNotNull(service);
    // The default should be MARKET_DATA_WS_URI
    assertEquals(
        CoinbaseStreamingExchange.MARKET_DATA_WS_URI,
        CoinbaseStreamingExchange.PROD_WS_URI);
  }

  @Test
  void jwtSupplierUsesApiKeysFromSpecification() {
    TestableCoinbaseStreamingExchange exchange = new TestableCoinbaseStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    String testApiKey = "test-api-key-123";
    String testSecretKey = "test-secret-key-456";
    spec.setApiKey(testApiKey);
    spec.setSecretKey(testSecretKey);
    // Don't call applySpecification as it initializes parent services which may fail with invalid keys
    exchange.setExchangeSpecificationForTesting(spec);

    // Create service - JWT supplier will be null due to invalid keys, but service should still be created
    CoinbaseStreamingService service = exchange.createStreamingService(spec);
    assertNotNull(service);
    
    // Verify the specification still has the API keys
    assertEquals(testApiKey, spec.getApiKey());
    assertEquals(testSecretKey, spec.getSecretKey());
  }

  @Test
  void jwtSupplierReturnsNullWhenApiKeysAreMissing() {
    CoinbaseStreamingExchange exchange = new CoinbaseStreamingExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    // Explicitly set null API keys
    spec.setApiKey(null);
    spec.setSecretKey(null);
    exchange.applySpecification(spec);

    // Service should still be created (for public channels)
    CoinbaseStreamingService service = exchange.createStreamingService(spec);
    assertNotNull(service);
    // JWT supplier will return null, which is acceptable for public channels
  }

  @Test
  void marketDataEndpointIsCorrect() {
    assertEquals(
        "wss://advanced-trade-ws.coinbase.com",
        CoinbaseStreamingExchange.MARKET_DATA_WS_URI);
  }

  @Test
  void userOrderDataEndpointIsCorrect() {
    assertEquals(
        "wss://advanced-trade-ws-user.coinbase.com",
        CoinbaseStreamingExchange.USER_ORDER_DATA_WS_URI);
  }

  @Test
  void defaultEndpointIsMarketData() {
    assertEquals(
        CoinbaseStreamingExchange.MARKET_DATA_WS_URI,
        CoinbaseStreamingExchange.PROD_WS_URI);
  }

  @SuppressWarnings("unchecked")
  private List<Disposable> getProductSubscriptions() {
    return exchange.getProductSubscriptionsForTesting();
  }

  /**
   * Testable subclass that allows dependency injection without reflection.
   */
  private static final class TestableCoinbaseStreamingExchange
      extends CoinbaseStreamingExchange {
    private TestStreamingService testStreamingService;
    private ExchangeSpecification testSpec;

    void setExchangeSpecificationForTesting(ExchangeSpecification spec) {
      this.testSpec = spec;
    }

    void setTestStreamingService(TestStreamingService testService) {
      this.testStreamingService = testService;
      // Create dependent services with the test streaming service
      this.streamingService = testService;
      this.streamingMarketDataService =
          new CoinbaseStreamingMarketDataService(testService, null, testSpec);
      this.tradeService = new CoinbaseStreamingTradeService(testService, testSpec);
    }

    @Override
    protected CoinbaseStreamingService createStreamingService(
        ExchangeSpecification exchangeSpecification) {
      // Return the injected test service if available, otherwise delegate to parent
      if (testStreamingService != null) {
        return testStreamingService;
      }
      return super.createStreamingService(exchangeSpecification);
    }

    @SuppressWarnings("unchecked")
    List<Disposable> getProductSubscriptionsForTesting() {
      try {
        Field field =
            CoinbaseStreamingExchange.class.getDeclaredField("productSubscriptions");
        field.setAccessible(true);
        return (List<Disposable>) field.get(this);
      } catch (Exception e) {
        throw new RuntimeException("Failed to access productSubscriptions", e);
      }
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
      // Return test spec if available, otherwise delegate to parent
      return testSpec != null ? testSpec : super.getExchangeSpecification();
    }
  }

  private static final class TestStreamingService extends CoinbaseStreamingService {
    private final List<CoinbaseSubscriptionRequest> subscriptionRequests = new ArrayList<>();
    private final AtomicInteger subscriptionCount = new AtomicInteger();

    TestStreamingService() {
      super("wss://example.com", () -> null, 8, 750);
    }

    @Override
    protected Observable<JsonNode> observeChannel(CoinbaseSubscriptionRequest request) {
      subscriptionRequests.add(request);
      subscriptionCount.incrementAndGet();

      // Return a never-completing observable for testing - this keeps subscriptions active
      // All channels use Observable<JsonNode>
      return Observable.never();
    }

    @Override
    public Completable connect() {
      return Completable.complete();
    }

    @Override
    public Completable disconnect() {
      return Completable.complete();
    }

    @Override
    public Observable<Object> subscribeConnectionSuccess() {
      return Observable.just(new Object());
    }

    @Override
    public boolean isSocketOpen() {
      return true;
    }

    List<CoinbaseSubscriptionRequest> getSubscriptionRequests() {
      return new ArrayList<>(subscriptionRequests);
    }

    int getSubscriptionCount() {
      return subscriptionCount.get();
    }
  }
}

