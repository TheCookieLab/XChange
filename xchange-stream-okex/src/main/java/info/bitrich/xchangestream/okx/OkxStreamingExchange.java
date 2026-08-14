package info.bitrich.xchangestream.okx;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.okx.OkxExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkxStreamingExchange extends OkxExchange implements StreamingExchange {

  private static final Logger LOG = LoggerFactory.getLogger(OkxStreamingExchange.class);

  // Production URIs
  public static final String WS_PUBLIC_CHANNEL_URI = "wss://ws.okx.com:8443/ws/v5/public";
  public static final String WS_PRIVATE_CHANNEL_URI = "wss://ws.okx.com:8443/ws/v5/private";
  public static final String WS_BUSINESS_CHANNEL_URI = "wss://ws.okx.com:8443/ws/v5/business";

  // Demo(Sandbox) URIs
  public static final String SANDBOX_WS_PUBLIC_CHANNEL_URI =
      "wss://wspap.okx.com:8443/ws/v5/public?brokerId=9999";
  public static final String SANDBOX_WS_PRIVATE_CHANNEL_URI =
      "wss://wspap.okx.com:8443/ws/v5/private?brokerId=9999";
  public static final String SANDBOX_WS_BUSINESS_CHANNEL_URI =
      "wss://wspap.okx.com:8443/ws/v5/business?brokerId=9999";

  private OkxStreamingService streamingService;

  private OkxStreamingMarketDataService streamingMarketDataService;

  private OkxStreamingTradeService streamingTradeService;

  private OkxPrivateStreamingService privateStreamingService;
  private OkxBusinessStreamingService businessStreamingService;

  /**
   * Transport availability snapshot captured when each facade was last built, so {@link
   * #connect()} can rebuild a facade when the transport set changes while a reconnect with
   * unchanged transports keeps the active subscriptions.
   */
  private volatile boolean marketDataFacadeHasBusinessTransport;
  private volatile boolean tradeServiceFacadeHasPrivateTransport;

  /**
   * Explicit transport configuration; {@code null} selects the default model (public + business,
   * plus private when credentials are present). An empty (non-null) set means "public only".
   */
  private volatile Set<TransportRole> requiredTransportsOverride;

  /** Monotonic counter of connection attempts; incremented on every {@link #connect()}. */
  private final AtomicLong connectionGeneration = new AtomicLong();

  public OkxStreamingExchange() {}

  @Override
  public Completable connect(ProductSubscription... args) {
    applyWebsocketTimeouts(exchangeSpecification);
    // Every connect() call establishes a new connection generation; consumers can use it to
    // discard events tied to a previous generation.
    connectionGeneration.incrementAndGet();
    Set<TransportRole> transports = getConnectionTransports();
    // Reuse already-created services so that a reconnect preserves the active channel
    // registrations and the netty layer re-subscribes them on the new socket.
    if (streamingService == null) {
      streamingService = new OkxStreamingService(getPublicApiUrl(), exchangeSpecification);
      applyStreamingSpecification(exchangeSpecification, streamingService);
    }
    if (transports.contains(TransportRole.PRIVATE) && privateStreamingService == null) {
      if (isApiKeyValid()) {
        privateStreamingService =
            new OkxPrivateStreamingService(getPrivateApiUrl(), exchangeSpecification, this);
        applyStreamingSpecification(exchangeSpecification, privateStreamingService);
      } else {
        LOG.warn("PRIVATE transport requested but no API credentials are configured; skipping");
      }
    }
    if (transports.contains(TransportRole.BUSINESS) && businessStreamingService == null) {
      businessStreamingService =
          new OkxBusinessStreamingService(getBusinessApiUrl(), exchangeSpecification);
      applyStreamingSpecification(exchangeSpecification, businessStreamingService);
    }
    boolean businessAvailable = businessStreamingService != null;
    if (streamingMarketDataService == null
        || marketDataFacadeHasBusinessTransport != businessAvailable) {
      streamingMarketDataService =
          new OkxStreamingMarketDataService(
              streamingService, businessStreamingService, exchangeMetaData);
      marketDataFacadeHasBusinessTransport = businessAvailable;
    }
    boolean privateAvailable = privateStreamingService != null;
    if (streamingTradeService == null || tradeServiceFacadeHasPrivateTransport != privateAvailable) {
      streamingTradeService =
          new OkxStreamingTradeService(
              privateStreamingService, exchangeMetaData, getResilienceRegistries());
      tradeServiceFacadeHasPrivateTransport = privateAvailable;
    }
    List<Completable> completableList = new ArrayList<>();
    completableList.add(streamingService.connect());
    if (transports.contains(TransportRole.BUSINESS) && businessStreamingService != null) {
      completableList.add(businessStreamingService.connect());
    }
    if (transports.contains(TransportRole.PRIVATE) && privateStreamingService != null) {
      completableList.add(privateStreamingService.connect());
    }
    return Completable.concat(completableList);
  }

  /**
   * Explicitly configures which transports must be connected and healthy.
   *
   * <p>With no explicit configuration the default model applies: public and business sockets are
   * always connected, and the private socket is connected when API credentials are configured. An
   * explicit configuration replaces the business/private defaults (the public socket is always
   * connected, so it does not need to be listed; an empty configuration means "public only").
   * {@link TransportRole#PRIVATE} is only effective when API credentials are configured, and {@link
   * TransportRole#BUSINESS} is additionally required whenever business subscriptions are active
   * regardless of the configuration.
   *
   * @param transports the transports to require; {@code null} restores the default model
   */
  public void setRequiredTransports(TransportRole... transports) {
    if (transports == null) {
      requiredTransportsOverride = null;
      return;
    }
    setRequiredTransports(new HashSet<>(Arrays.asList(transports)));
  }

  /**
   * Explicitly configures which transports must be connected and healthy; see {@link
   * #setRequiredTransports(TransportRole...)}.
   *
   * @param transports the transports to require; {@code null} restores the default model
   */
  public void setRequiredTransports(Set<TransportRole> transports) {
    if (transports == null) {
      requiredTransportsOverride = null;
      return;
    }
    Set<TransportRole> copy = new HashSet<>(transports);
    // PUBLIC is always required and does not need to be listed.
    copy.remove(TransportRole.PUBLIC);
    requiredTransportsOverride = copy;
  }

  /**
   * @return the transports that must currently be healthy for {@link #isAlive()} to return {@code
   *     true}. The public transport is always required; the default model additionally requires the
   *     business transport and the private transport once credentials are configured and private
   *     subscriptions are active. Explicit configuration replaces the defaults, while active
   *     business subscriptions always keep the business transport required.
   */
  public Set<TransportRole> getRequiredTransports() {
    Set<TransportRole> required = new HashSet<>();
    required.add(TransportRole.PUBLIC);
    if (requiredTransportsOverride == null) {
      required.add(TransportRole.BUSINESS);
    } else {
      required.addAll(requiredTransportsOverride);
    }
    if (privateStreamingService != null
        && ((requiredTransportsOverride != null
                && requiredTransportsOverride.contains(TransportRole.PRIVATE))
            || privateStreamingService.hasActiveChannels())) {
      required.add(TransportRole.PRIVATE);
    }
    if (businessStreamingService != null && businessStreamingService.hasActiveChannels()) {
      required.add(TransportRole.BUSINESS);
    }
    return required;
  }

  /**
   * @return the transports that {@link #connect(ProductSubscription...)} opens: the public
   *     transport plus the configured transports (or, by default, business and private when
   *     credentials are configured)
   */
  private Set<TransportRole> getConnectionTransports() {
    Set<TransportRole> transports = new HashSet<>();
    transports.add(TransportRole.PUBLIC);
    if (requiredTransportsOverride == null) {
      transports.add(TransportRole.BUSINESS);
      if (isApiKeyValid()) {
        transports.add(TransportRole.PRIVATE);
      }
    } else {
      transports.addAll(requiredTransportsOverride);
    }
    return transports;
  }

  /**
   * @return the generation of the most recent {@link #connect(ProductSubscription...)} call;
   *     incremented on every connect, {@code 0} before the first connect
   */
  public long getConnectionGeneration() {
    return connectionGeneration.get();
  }

  private boolean isApiKeyValid() {
    Object passphrase = exchangeSpecification.getExchangeSpecificParametersItem("passphrase");
    return exchangeSpecification.getApiKey() != null
        && !exchangeSpecification.getApiKey().isEmpty()
        && exchangeSpecification.getSecretKey() != null
        && !exchangeSpecification.getSecretKey().isEmpty()
        && passphrase != null
        && !passphrase.toString().isEmpty();
  }

  private String getPublicApiUrl() {
    String apiUrl;
    ExchangeSpecification exchangeSpec = getExchangeSpecification();
    if (exchangeSpec.getOverrideWebsocketApiUri() != null) {
      return exchangeSpec.getOverrideWebsocketApiUri();
    }
    if (useSandbox()) {
      apiUrl = SANDBOX_WS_PUBLIC_CHANNEL_URI;
    } else {
      apiUrl = WS_PUBLIC_CHANNEL_URI;
    }
    return apiUrl;
  }

  private String getPrivateApiUrl() {
    String apiUrl;
    if (useSandbox()) {
      apiUrl = SANDBOX_WS_PRIVATE_CHANNEL_URI;
    } else {
      apiUrl = WS_PRIVATE_CHANNEL_URI;
    }
    return apiUrl;
  }

  private String getBusinessApiUrl() {
    String apiUrl;
    if (useSandbox()) {
      apiUrl = SANDBOX_WS_BUSINESS_CHANNEL_URI;
    } else {
      apiUrl = WS_BUSINESS_CHANNEL_URI;
    }
    return apiUrl;
  }

  @Override
  public Completable disconnect() {
    List<Completable> completableList = new ArrayList<>();
    if (streamingService != null) {
      streamingService.pingPongDisconnectIfConnected();
      completableList.add(streamingService.disconnect());
    }
    if (privateStreamingService != null) {
      privateStreamingService.pingPongDisconnectIfConnected();
      completableList.add(privateStreamingService.disconnect());
    }
    if (businessStreamingService != null) {
      businessStreamingService.pingPongDisconnectIfConnected();
      completableList.add(businessStreamingService.disconnect());
    }
    return Completable.concat(completableList);
  }

  @Override
  public boolean isAlive() {
    if (streamingService == null) {
      return false;
    }
    Set<TransportRole> required = getRequiredTransports();
    if (required.contains(TransportRole.PUBLIC) && !streamingService.isSocketOpen()) {
      return false;
    }
    if (required.contains(TransportRole.BUSINESS)
        && (businessStreamingService == null || !businessStreamingService.isSocketOpen())) {
      return false;
    }
    if (required.contains(TransportRole.PRIVATE)
        && (privateStreamingService == null
            || !privateStreamingService.isSocketOpen()
            || !privateStreamingService.isLoginDone())) {
      return false;
    }
    return true;
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return streamingTradeService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    throw new NotYetImplementedForExchangeException("useCompressedMessage");
  }

  /**
   * Enables the user to listen on channel inactive events and react appropriately.
   *
   * @param channelInactiveHandler a WebSocketMessageHandler instance.
   */
  public void setChannelInactiveHandler(
      WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler) {
    if (streamingService != null) {
      streamingService.setChannelInactiveHandler(channelInactiveHandler);
    }
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return streamingService == null
        ? Observable.never()
        : streamingService.subscribeReconnectFailure();
  }

  @Override
  public Observable<ConnectionStateModel.State> connectionStateObservable() {
    return streamingService == null
        ? Observable.just(ConnectionStateModel.State.CLOSED)
        : streamingService.subscribeConnectionState();
  }

  public Observable<State> connectionStateObservablePrivateChannel() {
    return privateStreamingService == null
        ? Observable.just(State.CLOSED)
        : privateStreamingService.subscribeConnectionState();
  }

  public Observable<State> connectionStateObservableBusinessChannel() {
    return businessStreamingService == null
        ? Observable.just(State.CLOSED)
        : businessStreamingService.subscribeConnectionState();
  }

  @Override
  public void resubscribeChannels() {
    if (streamingService != null) {
      streamingService.resubscribeChannels();
    }
    if (privateStreamingService != null) {
      privateStreamingService.resubscribeChannels();
    }
    if (businessStreamingService != null) {
      businessStreamingService.resubscribeChannels();
    }
  }

  @Override
  public Observable<Object> connectionIdle() {
    return streamingService == null ? Observable.never() : streamingService.subscribeIdle();
  }

  // --- package-private test seams ------------------------------------------------------------

  void setStreamingService(OkxStreamingService streamingService) {
    this.streamingService = streamingService;
  }

  void setPrivateStreamingService(OkxPrivateStreamingService privateStreamingService) {
    this.privateStreamingService = privateStreamingService;
  }

  void setBusinessStreamingService(OkxBusinessStreamingService businessStreamingService) {
    this.businessStreamingService = businessStreamingService;
  }
}
