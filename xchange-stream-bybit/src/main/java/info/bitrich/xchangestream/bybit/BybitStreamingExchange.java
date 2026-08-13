package info.bitrich.xchangestream.bybit;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.config.BybitConfiguration;
import org.knowm.xchange.bybit.config.BybitEnvironment;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BybitStreamingExchange extends BybitExchange implements StreamingExchange {

  private final Logger LOG = LoggerFactory.getLogger(BybitStreamingExchange.class);

  // https://bybit-exchange.github.io/docs/v5/ws/connect
  public static final String URI = "wss://stream.bybit.com/v5/public";
  public static final String TESTNET_URI = "wss://stream-testnet.bybit.com/v5/public";
  // DEMO_URI without auth is the same as URI

  public static final String AUTH_URI = "wss://stream.bybit.com/v5/private";
  public static final String TESTNET_AUTH_URI = "wss://stream-testnet.bybit.com/v5/private";
  public static final String DEMO_AUTH_URI = "wss://stream-demo.bybit.com/v5/private";

  // websocket trade
  public static final String TRADE_URI = "wss://stream.bybit.com/v5/trade";
  public static final String TESTNET_TRADE_URI = "wss://stream-testnet.bybit.com/v5/trade";

  // spot, linear, inverse or option
  @Deprecated // use BybitConfiguration.EXCHANGE_TYPE
  public static final String EXCHANGE_TYPE = BybitConfiguration.EXCHANGE_TYPE;

  private BybitStreamingService streamingService;
  private BybitStreamingMarketDataService streamingMarketDataService;
  private BybitStreamingTradeService streamingTradeService;
  private BybitUserTradeStreamingService streamingUserTradeService;
  private BybitUserDataStreamingService streamingUserDataService;

  @Override
  protected void initServices() {
    super.initServices();
    // One validated configuration contract resolves REST and all WebSocket transports.
    BybitConfiguration configuration = BybitConfiguration.from(exchangeSpecification);
    BybitEnvironment environment = configuration.getEnvironment();
    BybitCategory category = BybitConfiguration.resolveStreamCategory(exchangeSpecification);
    applyWebsocketTimeouts(exchangeSpecification);
    this.streamingService =
        new BybitStreamingService(
            environment.getPublicWebsocketUrl(category), exchangeSpecification);
    applyStreamingSpecification(exchangeSpecification, streamingService);
    if (isApiKeyValid()) {
      this.streamingUserDataService =
          new BybitUserDataStreamingService(
              environment.getPrivateWebsocketUrl(), exchangeSpecification);
      applyStreamingSpecification(exchangeSpecification, streamingUserDataService);
      if (environment.supportsTradeWebsocket()) {
        this.streamingUserTradeService =
            new BybitUserTradeStreamingService(
                environment.getTradeWebsocketUrl(), exchangeSpecification);
        applyStreamingSpecification(exchangeSpecification, streamingUserTradeService);
      } else {
        LOG.warn(
            "Bybit demo trading does not support the WebSocket order-entry (trade) transport; "
                + "order-entry streaming is disabled. Use the REST trade service for order "
                + "operations in the demo environment.");
      }
    }
    this.streamingMarketDataService = new BybitStreamingMarketDataService(streamingService);
    this.streamingTradeService =
        new BybitStreamingTradeService(
            streamingUserDataService, streamingUserTradeService, getResilienceRegistries(), this);
  }

  private boolean isApiKeyValid() {
    return exchangeSpecification.getApiKey() != null
        && !exchangeSpecification.getApiKey().isEmpty()
        && exchangeSpecification.getSecretKey() != null
        && !exchangeSpecification.getSecretKey().isEmpty();
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    LOG.info("Connect to BybitStream");
    List<Completable> completableList = new ArrayList<>();
    completableList.add(streamingService.connect());
    if (isApiKeyValid()) {
      completableList.add(streamingUserDataService.connect());
      if (streamingUserTradeService != null) {
        completableList.add(streamingUserTradeService.connect());
      }
    }
    return Completable.concat(completableList);
  }

  @Override
  public Completable disconnect() {
    List<Completable> completableList = new ArrayList<>();
    if (streamingService != null) {
      streamingService.pingPongDisconnectIfConnected();
      completableList.add(streamingService.disconnect());
      streamingService = null;
    }
    if (streamingUserDataService != null) {
      streamingUserDataService.pingPongDisconnectIfConnected();
      completableList.add(streamingUserDataService.disconnect());
      streamingUserDataService = null;
    }
    if (streamingUserTradeService != null) {
      completableList.add(streamingUserTradeService.disconnect());
      streamingUserTradeService = null;
    }
    return Completable.concat(completableList);
  }

  @Override
  public BybitStreamingTradeService getStreamingTradeService() {
    if (streamingUserDataService != null && streamingUserDataService.isAuthorized()) {
      return streamingTradeService;
    } else {
      throw new IllegalArgumentException("Authentication required for private streams");
    }
  }

  /** Whether the order-entry (trade) transport was constructed (absent in demo trading). */
  boolean isTradeTransportEnabled() {
    return streamingUserTradeService != null;
  }

  @Override
  public boolean isAlive() {
    // In a normal situation - streamingService is always runs, userDataStreamingService - depends
    if (streamingService != null) {
      if (isApiKeyValid()) {
        return streamingService.isSocketOpen()
            && streamingUserDataService.isSocketOpen()
            && streamingUserDataService.isAuthorized()
            && (streamingUserTradeService == null
                || (streamingUserTradeService.isSocketOpen()
                    && streamingUserTradeService.isAuthorized()));
      } else {
        return streamingService.isSocketOpen();
      }
    }
    return false;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    streamingService.useCompressedMessages(compressedMessages);
  }

  @Override
  public BybitStreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return streamingService.subscribeReconnectFailure();
  }

  public Observable<Throwable> reconnectFailurePrivateChannel() {
    return streamingUserDataService.subscribeReconnectFailure();
  }

  @Override
  public Observable<State> connectionStateObservable() {
    return streamingService.subscribeConnectionState();
  }

  public Observable<State> connectionStateObservablePrivateChannel() {
    return streamingUserDataService.subscribeConnectionState();
  }

  public Observable<State> connectionStateObservableTradeChannel() {
    if (streamingUserTradeService == null) {
      // The demo environment has no order-entry (trade) transport.
      return Observable.empty();
    }
    return streamingUserTradeService.subscribeConnectionState();
  }

  @Override
  public void resubscribeChannels() {
    streamingService.resubscribeChannels();
    if (streamingUserDataService != null) {
      streamingUserDataService.resubscribeChannels();
    }
  }

  @Override
  public Observable<Object> connectionIdle() {
    return streamingService.subscribeIdle();
  }
}
