package info.bitrich.xchangestream.polymarket;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.polymarket.PolymarketExchange;

/**
 * Polymarket streaming exchange over the two CLOB WebSocket channels. The public market channel
 * (books, price changes, last trades) connects anonymously; the user channel (order and trade
 * updates) shares the REST credential model: {@code apiKey}, {@code secretKey}, and {@code
 * password} form the L2 API credential triplet derived from the signer's wallet.
 *
 * <p>Without the full credential triplet the exchange runs in <em>public-only</em> mode: only the
 * market socket is created, the user service stays {@code null}, and health and lifecycle
 * observables cover the market connection alone. The server may close an unsubscribed user
 * connection (docs.polymarket.com/trading/realtime-order-updates), so an unusable user socket must
 * never be created, health-checked, or reconnected in public-only mode. {@link
 * #getStreamingTradeService()} returns {@code null} then; with credentials it returns the
 * authenticated user-channel service.
 */
public class PolymarketStreamingExchange extends PolymarketExchange implements StreamingExchange {

  /** Production market-channel WebSocket URI. */
  public static final String WS_MARKET_URI = "wss://ws-subscriptions-clob.polymarket.com/ws/market";

  /** Production user-channel WebSocket URI. */
  public static final String WS_USER_URI = "wss://ws-subscriptions-clob.polymarket.com/ws/user";

  /** Exchange-specific parameter overriding the market-channel WebSocket URI. */
  public static final String PARAM_WS_MARKET_URI = "polymarket.ws.market.uri";

  /** Exchange-specific parameter overriding the user-channel WebSocket URI. */
  public static final String PARAM_WS_USER_URI = "polymarket.ws.user.uri";

  private PolymarketStreamingService marketStreamingService;
  private PolymarketStreamingService userStreamingService;
  private PolymarketStreamingMarketDataService streamingMarketDataService;
  private PolymarketStreamingTradeService streamingTradeService;

  @Override
  protected void initServices() {
    super.initServices();
    ExchangeSpecification spec = getExchangeSpecification();
    marketStreamingService =
        createStreamingService(resolveUri(PARAM_WS_MARKET_URI, WS_MARKET_URI), null, null, null);
    applyStreamingSpecification(spec, marketStreamingService);
    streamingMarketDataService = new PolymarketStreamingMarketDataService(marketStreamingService);
    if (hasCredentials(spec)) {
      userStreamingService =
          createStreamingService(
              resolveUri(PARAM_WS_USER_URI, WS_USER_URI),
              spec.getApiKey(),
              spec.getSecretKey(),
              spec.getPassword());
      applyStreamingSpecification(spec, userStreamingService);
      streamingTradeService = new PolymarketStreamingTradeService(userStreamingService);
    }
    // Without the full credential triplet both user fields stay null (public-only mode):
    // no user socket is created, connected, or health-checked.
  }

  /**
   * Creates the service for one CLOB channel; overridable so tests can substitute a fake service
   * and exercise the lifecycle without a socket.
   */
  protected PolymarketStreamingService createStreamingService(
      String apiUrl, String apiKey, String secret, String passphrase) {
    return new PolymarketStreamingService(apiUrl, apiKey, secret, passphrase);
  }

  private static boolean hasCredentials(ExchangeSpecification spec) {
    return spec.getApiKey() != null
        && !spec.getApiKey().isBlank()
        && spec.getSecretKey() != null
        && !spec.getSecretKey().isBlank()
        && spec.getPassword() != null
        && !spec.getPassword().isBlank();
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    return userStreamingService == null
        ? marketStreamingService.connect()
        : Completable.mergeArray(
            marketStreamingService.connect(), userStreamingService.connect());
  }

  @Override
  public Completable disconnect() {
    return userStreamingService == null
        ? marketStreamingService.disconnect()
        : Completable.mergeArray(
            marketStreamingService.disconnect(), userStreamingService.disconnect());
  }

  @Override
  public boolean isAlive() {
    return marketStreamingService.isSocketOpen()
        && (userStreamingService == null || userStreamingService.isSocketOpen());
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return userStreamingService == null
        ? marketStreamingService.subscribeReconnectFailure()
        : Observable.merge(
            marketStreamingService.subscribeReconnectFailure(),
            userStreamingService.subscribeReconnectFailure());
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return userStreamingService == null
        ? marketStreamingService.subscribeConnectionSuccess()
        : Observable.merge(
            marketStreamingService.subscribeConnectionSuccess(),
            userStreamingService.subscribeConnectionSuccess());
  }

  @Override
  public Observable<Object> disconnectObservable() {
    return userStreamingService == null
        ? marketStreamingService.subscribeDisconnect()
        : Observable.merge(
            marketStreamingService.subscribeDisconnect(), userStreamingService.subscribeDisconnect());
  }

  @Override
  public PolymarketStreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  /**
   * @return the authenticated user-channel trade service, or {@code null} in public-only mode
   *     (no L2 credential triplet on the exchange specification)
   */
  @Override
  public PolymarketStreamingTradeService getStreamingTradeService() {
    return streamingTradeService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    marketStreamingService.useCompressedMessages(compressedMessages);
    if (userStreamingService != null) {
      userStreamingService.useCompressedMessages(compressedMessages);
    }
  }
}
