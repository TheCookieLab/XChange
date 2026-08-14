package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingExchange;
import info.bitrich.xchangestream.okx.OkxStreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingTradeService;
import info.bitrich.xchangestream.okx.TransportRole;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.util.Set;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.okex.OkexExchange;

/**
 * @deprecated use {@link info.bitrich.xchangestream.okx.OkxStreamingExchange} instead.
 */
@Deprecated
public class OkexStreamingExchange extends OkexExchange implements StreamingExchange {

  // Production URIs
  public static final String WS_PUBLIC_CHANNEL_URI = OkxStreamingExchange.WS_PUBLIC_CHANNEL_URI;
  public static final String WS_PRIVATE_CHANNEL_URI = OkxStreamingExchange.WS_PRIVATE_CHANNEL_URI;
  public static final String WS_BUSINESS_CHANNEL_URI = OkxStreamingExchange.WS_BUSINESS_CHANNEL_URI;

  // Demo(Sandbox) URIs
  public static final String SANDBOX_WS_PUBLIC_CHANNEL_URI =
      OkxStreamingExchange.SANDBOX_WS_PUBLIC_CHANNEL_URI;
  public static final String SANDBOX_WS_PRIVATE_CHANNEL_URI =
      OkxStreamingExchange.SANDBOX_WS_PRIVATE_CHANNEL_URI;
  public static final String SANDBOX_WS_BUSINESS_CHANNEL_URI =
      OkxStreamingExchange.SANDBOX_WS_BUSINESS_CHANNEL_URI;

  /**
   * Canonical streaming exchange backing this deprecated shim. The delegate shares the legacy
   * exchange specification and runs its own {@code remoteInit} so streaming services observe the
   * same live exchange metadata as the legacy side.
   */
  private final OkxStreamingExchange delegate = new OkxStreamingExchange();

  public OkexStreamingExchange() {}

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    super.applySpecification(exchangeSpecification);
    delegate.applySpecification(exchangeSpecification);
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    return delegate.connect(args);
  }

  @Override
  public Completable disconnect() {
    return delegate.disconnect();
  }

  @Override
  public boolean isAlive() {
    return delegate.isAlive();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    OkxStreamingMarketDataService okxStreamingMarketDataService =
        (OkxStreamingMarketDataService) delegate.getStreamingMarketDataService();
    return okxStreamingMarketDataService == null
        ? null
        : new OkexStreamingMarketDataService(okxStreamingMarketDataService);
  }

  @Override
  public OkexStreamingTradeService getStreamingTradeService() {
    OkxStreamingTradeService okxStreamingTradeService =
        (OkxStreamingTradeService) delegate.getStreamingTradeService();
    return okxStreamingTradeService == null
        ? null
        : new OkexStreamingTradeService(okxStreamingTradeService);
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    delegate.useCompressedMessages(compressedMessages);
  }

  /**
   * Enables the user to listen on channel inactive events and react appropriately.
   *
   * @param channelInactiveHandler a WebSocketMessageHandler instance.
   */
  public void setChannelInactiveHandler(
      WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler) {
    delegate.setChannelInactiveHandler(channelInactiveHandler);
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return delegate.reconnectFailure();
  }

  @Override
  public Observable<ConnectionStateModel.State> connectionStateObservable() {
    return delegate.connectionStateObservable();
  }

  public Observable<State> connectionStateObservablePrivateChannel() {
    return delegate.connectionStateObservablePrivateChannel();
  }

  public Observable<State> connectionStateObservableBusinessChannel() {
    return delegate.connectionStateObservableBusinessChannel();
  }

  @Override
  public void resubscribeChannels() {
    delegate.resubscribeChannels();
  }

  @Override
  public Observable<Object> connectionIdle() {
    return delegate.connectionIdle();
  }

  public long getConnectionGeneration() {
    return delegate.getConnectionGeneration();
  }

  public void setRequiredTransports(TransportRole... transports) {
    delegate.setRequiredTransports(transports);
  }

  public void setRequiredTransports(Set<TransportRole> transports) {
    delegate.setRequiredTransports(transports);
  }

  public Set<TransportRole> getRequiredTransports() {
    return delegate.getRequiredTransports();
  }
}
