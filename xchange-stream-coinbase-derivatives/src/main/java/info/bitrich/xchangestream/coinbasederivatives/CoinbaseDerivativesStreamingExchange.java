package info.bitrich.xchangestream.coinbasederivatives;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesJwtGenerator;

/** Streaming exchange for Coinbase Advanced international derivatives on Starbase. */
public class CoinbaseDerivativesStreamingExchange extends CoinbaseDerivativesExchange
    implements StreamingExchange {

  public static final String DEFAULT_WEBSOCKET_URI = "wss://drb.coinbase.com/ws/api/v2";
  public static final String PARAM_WEBSOCKET_URI = "WebsocketUri";
  public static final String PARAM_CANCEL_ON_DISCONNECT = "CancelOnDisconnect";
  public static final String PARAM_CANCEL_ON_DISCONNECT_SCOPE = "CancelOnDisconnectScope";

  private CoinbaseDerivativesStreamingService streamingService;
  private CoinbaseDerivativesStreamingMarketDataService streamingMarketDataService;
  private CoinbaseDerivativesStreamingAccountService streamingAccountService;
  private CoinbaseDerivativesStreamingTradeService streamingTradeService;

  @Override
  public Completable connect(ProductSubscription... args) {
    ExchangeSpecification specification = getExchangeSpecification();
    CoinbaseDerivativesStreamConfiguration configuration = configuration(specification);
    String websocketUri =
        stringParameter(specification, PARAM_WEBSOCKET_URI, DEFAULT_WEBSOCKET_URI);
    streamingService = new CoinbaseDerivativesStreamingService(websocketUri, configuration);
    applyStreamingSpecification(specification, streamingService);
    streamingMarketDataService =
        new CoinbaseDerivativesStreamingMarketDataService(streamingService);
    streamingAccountService = new CoinbaseDerivativesStreamingAccountService(streamingService);
    streamingTradeService = new CoinbaseDerivativesStreamingTradeService(streamingService);
    return streamingService.connect();
  }

  @Override
  public Completable disconnect() {
    if (streamingService == null) {
      return Completable.complete();
    }
    CoinbaseDerivativesStreamingService service = streamingService;
    return service.disconnect();
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
    return streamingService == null ? Observable.empty() : streamingService.subscribeDisconnect();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public StreamingAccountService getStreamingAccountService() {
    return streamingAccountService;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return streamingTradeService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (streamingService != null) {
      streamingService.useCompressedMessages(compressedMessages);
    }
  }

  private CoinbaseDerivativesStreamConfiguration configuration(
      ExchangeSpecification specification) {
    Supplier<String> jwtSupplier = null;
    if (specification.getApiKey() != null
        && !specification.getApiKey().isBlank()
        && specification.getSecretKey() != null
        && !specification.getSecretKey().isBlank()) {
      CoinbaseDerivativesJwtGenerator generator =
          new CoinbaseDerivativesJwtGenerator(
              specification.getApiKey(), specification.getSecretKey());
      jwtSupplier = generator::generate;
    }
    boolean cancelOnDisconnect =
        Boolean.parseBoolean(stringParameter(specification, PARAM_CANCEL_ON_DISCONNECT, "false"));
    String scope = stringParameter(specification, PARAM_CANCEL_ON_DISCONNECT_SCOPE, "CONNECTION");
    CoinbaseDerivativesStreamConfiguration.CancelOnDisconnectScope cancelScope =
        CoinbaseDerivativesStreamConfiguration.CancelOnDisconnectScope.valueOf(
            scope.toUpperCase(Locale.ROOT));
    return new CoinbaseDerivativesStreamConfiguration(
        jwtSupplier,
        Duration.ofMinutes(50),
        Duration.ofMinutes(5),
        cancelOnDisconnect,
        cancelScope);
  }

  private String stringParameter(
      ExchangeSpecification specification, String key, String defaultValue) {
    Object value = specification.getExchangeSpecificParametersItem(key);
    return value == null ? defaultValue : value.toString();
  }
}
