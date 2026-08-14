package info.bitrich.xchangestream.mexc;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.mexc.v3.MexcV3Exchange;
import org.knowm.xchange.mexc.v3.service.MexcV3MarketDataServiceRaw;

/**
 * Streaming exchange for MEXC Spot v3.
 *
 * <p>Public market data streams connect to {@value #DEFAULT_WEBSOCKET_URI}. Private streams
 * (account/deals/orders) require a listen key and are wired in a follow-up change; until then
 * {@link #getStreamingAccountService()} and {@link #getStreamingTradeService()} return {@code
 * null}.
 */
public class MexcV3StreamingExchange extends MexcV3Exchange implements StreamingExchange {

  /** MEXC Spot v3 public WebSocket endpoint. */
  public static final String DEFAULT_WEBSOCKET_URI = "wss://wbs-api.mexc.com/ws";
  /** Exchange-specific parameter key to override the WebSocket URI. */
  public static final String PARAM_WEBSOCKET_URI = "WebsocketUri";

  private MexcV3StreamingService streamingService;
  private MexcV3StreamingMarketDataService streamingMarketDataService;

  @Override
  public Completable connect(ProductSubscription... args) {
    if (streamingService == null) {
      ExchangeSpecification specification = getExchangeSpecification();
      String uri =
          (String) specification.getExchangeSpecificParametersItem(PARAM_WEBSOCKET_URI);
      if (uri == null || uri.isBlank()) {
        uri = DEFAULT_WEBSOCKET_URI;
      }
      streamingService = new MexcV3StreamingService(uri);
      applyStreamingSpecification(specification, streamingService);
      streamingMarketDataService =
          new MexcV3StreamingMarketDataService(
              streamingService, (MexcV3MarketDataServiceRaw) getMarketDataService());
    }
    return streamingService.connect();
  }

  @Override
  public Completable disconnect() {
    if (streamingService == null) {
      return Completable.complete();
    }
    return streamingService.disconnect();
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
    return streamingService == null
        ? Observable.empty()
        : streamingService.subscribeDisconnect();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public StreamingAccountService getStreamingAccountService() {
    return null;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return null;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (streamingService != null) {
      streamingService.useCompressedMessages(compressedMessages);
    }
  }
}
