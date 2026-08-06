package info.bitrich.xchangestream.kalshi;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.client.KalshiDigest;

/**
 * Kalshi streaming exchange over the trade-api v2 WebSocket. Shares the REST credential model:
 * {@code apiKey} is the Kalshi API key id and {@code secretKey} the unencrypted PKCS#8 RSA private
 * key used to sign the WebSocket handshake. Kalshi requires credentials for every WebSocket
 * session — public market-data channels included — so {@link #initServices()} fails fast with an
 * {@link ExchangeSecurityException} when either credential half is missing; public channels remain
 * subscribeable, but only over the authenticated session.
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
    requireCredentials(spec);
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

  private static void requireCredentials(ExchangeSpecification spec) {
    if (spec.getApiKey() == null || spec.getApiKey().isBlank()) {
      throw new ExchangeSecurityException(
          "Kalshi streaming requires credentials even for public market-data channels: set the"
              + " Kalshi API key id on the exchange specification (apiKey)");
    }
    if (spec.getSecretKey() == null || spec.getSecretKey().isBlank()) {
      throw new ExchangeSecurityException(
          "Kalshi streaming requires credentials even for public market-data channels: set the"
              + " unencrypted PKCS#8 RSA private key on the exchange specification (secretKey)");
    }
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
