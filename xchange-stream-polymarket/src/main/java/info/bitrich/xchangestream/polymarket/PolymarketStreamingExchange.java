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
 * password} form the L2 API credential triplet derived from the signer's wallet. Without
 * credentials only the market channel may be subscribed.
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
        new PolymarketStreamingService(
            resolveUri(PARAM_WS_MARKET_URI, WS_MARKET_URI), null, null, null);
    userStreamingService =
        new PolymarketStreamingService(
            resolveUri(PARAM_WS_USER_URI, WS_USER_URI),
            spec.getApiKey(),
            spec.getSecretKey(),
            spec.getPassword());
    applyStreamingSpecification(spec, marketStreamingService);
    applyStreamingSpecification(spec, userStreamingService);
    streamingMarketDataService = new PolymarketStreamingMarketDataService(marketStreamingService);
    streamingTradeService = new PolymarketStreamingTradeService(userStreamingService);
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    return Completable.mergeArray(
        marketStreamingService.connect(), userStreamingService.connect());
  }

  @Override
  public Completable disconnect() {
    return Completable.mergeArray(
        marketStreamingService.disconnect(), userStreamingService.disconnect());
  }

  @Override
  public boolean isAlive() {
    return marketStreamingService.isSocketOpen() && userStreamingService.isSocketOpen();
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return Observable.merge(
        marketStreamingService.subscribeReconnectFailure(),
        userStreamingService.subscribeReconnectFailure());
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return Observable.merge(
        marketStreamingService.subscribeConnectionSuccess(),
        userStreamingService.subscribeConnectionSuccess());
  }

  @Override
  public Observable<Object> disconnectObservable() {
    return Observable.merge(
        marketStreamingService.subscribeDisconnect(), userStreamingService.subscribeDisconnect());
  }

  @Override
  public PolymarketStreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public PolymarketStreamingTradeService getStreamingTradeService() {
    return streamingTradeService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    marketStreamingService.useCompressedMessages(compressedMessages);
    userStreamingService.useCompressedMessages(compressedMessages);
  }
}
