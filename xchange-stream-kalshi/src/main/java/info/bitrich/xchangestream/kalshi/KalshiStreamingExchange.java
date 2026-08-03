package info.bitrich.xchangestream.kalshi;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.client.KalshiDigest;

/**
 * Kalshi streaming exchange over the trade-api v2 WebSocket. Shares the REST credential model:
 * {@code apiKey} is the Kalshi API key id and {@code secretKey} the unencrypted PKCS#8 RSA private
 * key used to sign the WebSocket handshake. Without credentials the connection is anonymous and
 * only the public channels (order book, trades, ticker, market lifecycle) may be subscribed.
 */
public class KalshiStreamingExchange extends KalshiExchange implements StreamingExchange {

  /** Production WebSocket URI. */
  public static final String WS_URI = "wss://external-api-ws.kalshi.com/trade-api/ws/v2";

  /** Exchange-specific parameter overriding the WebSocket URI, for example the demo host. */
  public static final String PARAM_WS_URI = "kalshi.ws.uri";

  private KalshiStreamingService streamingService;
  private KalshiStreamingMarketDataService streamingMarketDataService;
  private KalshiStreamingTradeService streamingTradeService;

  @Override
  protected void initServices() {
    super.initServices();
    ExchangeSpecification spec = getExchangeSpecification();
    Object override = spec.getExchangeSpecificParametersItem(PARAM_WS_URI);
    streamingService =
        new KalshiStreamingService(
            override == null ? WS_URI : override.toString(),
            spec.getApiKey(),
            KalshiDigest.createInstance(spec.getSecretKey()));
    applyStreamingSpecification(spec, streamingService);
    streamingMarketDataService = new KalshiStreamingMarketDataService(streamingService);
    streamingTradeService = new KalshiStreamingTradeService(streamingService);
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    return streamingService.connect();
  }

  @Override
  public Completable disconnect() {
    return streamingService.disconnect();
  }

  @Override
  public boolean isAlive() {
    return streamingService.isSocketOpen();
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return streamingService.subscribeReconnectFailure();
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return streamingService.subscribeConnectionSuccess();
  }

  @Override
  public Observable<Object> disconnectObservable() {
    return streamingService.subscribeDisconnect();
  }

  @Override
  public KalshiStreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public KalshiStreamingTradeService getStreamingTradeService() {
    return streamingTradeService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    streamingService.useCompressedMessages(compressedMessages);
  }
}
