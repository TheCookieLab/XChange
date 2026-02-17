package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.utils.AuthUtils;

/**
 * Integration coverage for Coinbase Advanced Trade websocket connectivity.
 *
 * <p>Coinbase documents sandbox endpoints for REST, but websocket usage is documented against
 * production websocket hosts. This suite therefore validates public channel connectivity against
 * production websocket endpoints and conditionally validates private channels when real credentials
 * are available.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CoinbaseStreamingConnectivityIntegration {

  private static final CurrencyPair TEST_PAIR = CurrencyPair.BTC_USD;
  private static final String TEST_PRODUCT_ID = "BTC-USD";
  private static final long HEALTH_WINDOW_SECONDS = 4L;
  private static final long SUBSCRIPTION_COOLDOWN_MILLIS = 400L;

  private CoinbaseStreamingExchange publicExchange;
  private CoinbaseStreamingService publicStreamingService;
  private CoinbaseStreamingMarketDataService publicMarketDataService;

  @BeforeAll
  void beforeAll() {
    ExchangeSpecification spec = new CoinbaseStreamingExchange().getDefaultExchangeSpecification();
    spec.setExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_DEFAULT_CANDLE_GRANULARITY,
        CoinbaseCandleGranularity.ONE_MINUTE.name());
    spec.setExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_DEFAULT_CANDLE_PRODUCT_TYPE, "SPOT");

    publicExchange =
        (CoinbaseStreamingExchange) StreamingExchangeFactory.INSTANCE.createExchange(spec);
    publicExchange.connect().blockingAwait();

    publicStreamingService = publicExchange.streamingService;
    publicMarketDataService = publicExchange.getStreamingMarketDataService();
    assertNotNull(publicStreamingService);
    assertNotNull(publicMarketDataService);
  }

  @AfterAll
  void afterAll() {
    if (publicExchange != null) {
      publicExchange.disconnect().blockingAwait();
    }
  }

  /**
   * Exercises all public channels available in {@link CoinbaseChannel}.
   */
  @Test
  void testPublicChannelConnectivityMatrix() {
    assertSubscriptionHealthy(CoinbaseChannel.HEARTBEATS, Collections.emptyList(), Collections.emptyMap());
    assertSubscriptionHealthy(CoinbaseChannel.TICKER, Collections.singletonList(TEST_PRODUCT_ID), Collections.emptyMap());
    assertSubscriptionHealthy(CoinbaseChannel.TICKER_BATCH, Collections.singletonList(TEST_PRODUCT_ID), Collections.emptyMap());
    assertSubscriptionHealthy(CoinbaseChannel.MARKET_TRADES, Collections.singletonList(TEST_PRODUCT_ID), Collections.emptyMap());
    assertSubscriptionHealthy(CoinbaseChannel.LEVEL2, Collections.singletonList(TEST_PRODUCT_ID), Collections.emptyMap());
    assertSubscriptionHealthy(CoinbaseChannel.LEVEL2_BATCH, Collections.singletonList(TEST_PRODUCT_ID), Collections.emptyMap());

    assertSubscriptionHealthy(
        CoinbaseChannel.L2_DATA,
        Collections.singletonList(TEST_PRODUCT_ID),
        Collections.emptyMap());
    assertSubscriptionHealthy(CoinbaseChannel.STATUS, Collections.emptyList(), Collections.emptyMap());
    assertSubscriptionHealthy(
        CoinbaseChannel.CANDLES,
        Collections.singletonList(TEST_PRODUCT_ID),
        new CoinbaseCandleSubscriptionParams(CoinbaseCandleGranularity.ONE_MINUTE).toChannelArgs());
    assertSubscriptionHealthy(
        CoinbaseChannel.CANDLES,
        Collections.singletonList(TEST_PRODUCT_ID),
        new CoinbaseCandleSubscriptionParams(CoinbaseCandleGranularity.ONE_MINUTE, "SPOT")
            .toChannelArgs());
  }

  /**
   * Exercises streaming market-data service methods and argument variations.
   */
  @Test
  void testMarketDataServiceVariations() {
    assertStreamEmitsOrRemainsHealthy(publicMarketDataService.getTicker(TEST_PAIR));
    assertStreamEmitsOrRemainsHealthy(publicMarketDataService.getTrades(TEST_PAIR));
    assertStreamEmitsOrRemainsHealthy(publicMarketDataService.getOrderBook(TEST_PAIR));
    assertStreamEmitsOrRemainsHealthy(publicMarketDataService.getOrderBookBatch(TEST_PAIR));
    assertStreamEmitsOrRemainsHealthy(publicMarketDataService.getOrderBook(TEST_PAIR, "level2_batch"));

    assertStreamHealthy(
        publicMarketDataService.getCandles(TEST_PAIR, CoinbaseCandleGranularity.ONE_MINUTE));
    assertStreamHealthy(publicMarketDataService.getCandles(TEST_PAIR, Duration.ofMinutes(1)));
    assertStreamHealthy(publicMarketDataService.getCandles(TEST_PAIR, "ONE_MINUTE"));
    assertStreamHealthy(publicMarketDataService.getCandles(TEST_PAIR, "ONE_MINUTE", "SPOT"));
  }

  /**
   * Private channels require real production credentials and the user websocket endpoint.
   * This coverage is executed when credentials are available; otherwise it is skipped.
   */
  @Test
  void testPrivateChannelConnectivityWhenCredentialsAvailable() {
    ExchangeSpecification spec = new CoinbaseStreamingExchange().getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(spec);
    Assumptions.assumeTrue(
        spec.getApiKey() != null
            && !spec.getApiKey().isEmpty()
            && spec.getSecretKey() != null
            && !spec.getSecretKey().isEmpty(),
        "No Coinbase production credentials configured for private websocket coverage");

    spec.setOverrideWebsocketApiUri(CoinbaseStreamingExchange.USER_ORDER_DATA_WS_URI);
    CoinbaseStreamingExchange privateExchange =
        (CoinbaseStreamingExchange) StreamingExchangeFactory.INSTANCE.createExchange(spec);

    try {
      privateExchange.connect().blockingAwait();
      CoinbaseStreamingService privateStreamingService = privateExchange.streamingService;
      assertNotNull(privateStreamingService);

      assertSubscriptionHealthy(
          privateStreamingService,
          CoinbaseChannel.USER,
          Collections.singletonList(TEST_PRODUCT_ID),
          Collections.emptyMap());
      assertSubscriptionHealthy(
          privateStreamingService,
          CoinbaseChannel.FUTURES_BALANCE_SUMMARY,
          Collections.emptyList(),
          Collections.emptyMap());
    } finally {
      privateExchange.disconnect().blockingAwait();
    }
  }

  private <T> void assertStreamEmitsOrRemainsHealthy(Observable<T> stream) {
    assertStreamHealthy(stream);
    coolDown();
  }

  private void assertStreamHealthy(Observable<?> stream) {
    TestObserver<?> observer = stream.test();
    observer.awaitDone(HEALTH_WINDOW_SECONDS, TimeUnit.SECONDS);
    observer.assertNoErrors();
    observer.dispose();
  }

  private void assertSubscriptionHealthy(
      CoinbaseChannel channel, java.util.List<String> productIds, Map<String, Object> args) {
    assertSubscriptionHealthy(publicStreamingService, channel, productIds, args);
  }

  private void assertSubscriptionHealthy(
      CoinbaseStreamingService service,
      CoinbaseChannel channel,
      java.util.List<String> productIds,
      Map<String, Object> args) {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(channel, productIds, args);
    TestObserver<JsonNode> observer = service.observeChannel(request).test();
    observer.awaitDone(HEALTH_WINDOW_SECONDS, TimeUnit.SECONDS);
    observer.assertNoErrors();
    observer.dispose();
    coolDown();
  }

  private void coolDown() {
    try {
      TimeUnit.MILLISECONDS.sleep(SUBSCRIPTION_COOLDOWN_MILLIS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while pacing websocket subscriptions", ex);
    }
  }
}
