package info.bitrich.xchangestream.cryptocom;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.knowm.xchange.cryptocom.CryptoComAdapters;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crypto.com Exchange v1 streaming connector over the official market ({@code
 * /exchange/v1/market}) and user ({@code /exchange/v1/user}) WebSocket feeds.
 *
 * <p><strong>Endpoints.</strong> Only the production hosts are hard-coded. Crypto.com does not
 * publish a verified sandbox streaming host, so {@link StreamingExchange#USE_SANDBOX} alone never
 * selects an endpoint: enabling sandbox without an explicit override fails the connection
 * (fail-closed). A caller that has its own verified WebSocket base URL (for example a UAT host
 * provided by Crypto.com support) opts in explicitly with the {@value #CRYPTOCOM_WS_OVERRIDE_URI}
 * specification parameter; the market and user paths are appended to the base URL.
 *
 * <p><strong>Transports.</strong> The private user socket is created only when the {@link
 * ProductSubscription} passed to {@link #connect(ProductSubscription...)} declares authenticated
 * channels (orders, user trades, balances) - never merely because API credentials happen to be
 * configured. Public-only subscriptions work without a private socket. Requesting authenticated
 * channels without credentials fails the connection explicitly.
 *
 * <p><strong>Liveness.</strong> {@link #isAlive()} aggregates the state of every required
 * transport: the public socket must be open on the current connection generation, an opened
 * private socket must additionally be authenticated, and every channel requested via the connect
 * subscription must have been confirmed by the server. See {@link
 * CryptoComStreamingService#isCurrentConnection()} for the stale-generation contract.
 */
public class CryptoComStreamingExchange extends CryptoComExchange implements StreamingExchange {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoComStreamingExchange.class);

  public static final String PUBLIC_WS_URL = "wss://stream.crypto.com/exchange/v1/market";
  public static final String PRIVATE_WS_URL = "wss://stream.crypto.com/exchange/v1/user";

  /**
   * Exchange specification parameter: an explicit, caller-verified WebSocket base URL. Setting it
   * is the only way to use a non-production streaming endpoint (including sandbox/UAT); the
   * exchange appends {@code /exchange/v1/market} and {@code /exchange/v1/user}.
   */
  public static final String CRYPTOCOM_WS_OVERRIDE_URI = "cryptocom_ws_override";

  /** Default book depth used when connecting requested order-book channels. */
  static final int DEFAULT_BOOK_DEPTH = 10;

  private CryptoComStreamingService publicStreamingService;
  /** True between {@link #disconnect()} and the next {@link #connect(ProductSubscription)}. */
  private boolean servicesDisposed;
  private CryptoComPrivateStreamingService privateStreamingService;

  private CryptoComStreamingMarketDataService streamingMarketDataService;
  private CryptoComStreamingTradeService streamingTradeService;
  private CryptoComStreamingAccountService streamingAccountService;
  private CryptoComStreamingEventDeduplicator eventDeduplicator;

  private boolean privateRequired;
  private final Set<String> requiredChannels = new HashSet<>();
  private final List<Disposable> subscriptionDisposables = new ArrayList<>();

  @Override
  public Completable connect(ProductSubscription... args) {
    ProductSubscription subscription = args != null && args.length > 0 ? args[0] : null;
    return connect(subscription);
  }

  private Completable connect(ProductSubscription subscription) {
    servicesDisposed = false;
    // --- Resolve endpoints and fail closed before any transport is created. ---
    String overrideBaseUrl = (String) exchangeSpecification.getParameter(CRYPTOCOM_WS_OVERRIDE_URI);
    if (usingSandbox() && overrideBaseUrl == null) {
      return Completable.error(
          new ExchangeException(
              "Crypto.com does not publish a verified sandbox WebSocket host and the streaming "
                  + "connector fails closed instead of guessing one. Opt in with an explicit, "
                  + "verified base URL via the '"
                  + CRYPTOCOM_WS_OVERRIDE_URI
                  + "' exchange specification parameter."));
    }
    String publicWsUrl = publicWsUrl(overrideBaseUrl);
    String privateWsUrl = privateWsUrl(overrideBaseUrl);

    // --- Derive the required transports from the subscription, not from credentials. ---
    privateRequired = privateTransportRequired(subscription);
    requiredChannels.clear();
    if (subscription != null) {
      requiredChannels.addAll(channelsFor(subscription));
    }
    if (privateRequired) {
      String apiKey = exchangeSpecification.getApiKey();
      String secretKey = exchangeSpecification.getSecretKey();
      if (apiKey == null || secretKey == null) {
        return Completable.error(
            new ExchangeSecurityException(
                "Authenticated streaming subscriptions (orders, user trades, balances) require "
                    + "API credentials, but none are configured."));
      }
    }

    publicStreamingService = new CryptoComStreamingService(publicWsUrl);
    applyStreamingSpecification(exchangeSpecification, publicStreamingService);
    streamingMarketDataService = new CryptoComStreamingMarketDataService(publicStreamingService);
    eventDeduplicator = new CryptoComStreamingEventDeduplicator();
    Completable publicConnect = publicStreamingService.connect();

    Completable privateConnect = Completable.complete();
    if (privateRequired) {
      String apiKey = exchangeSpecification.getApiKey();
      String secretKey = exchangeSpecification.getSecretKey();
      privateStreamingService =
          new CryptoComPrivateStreamingService(privateWsUrl, apiKey, secretKey);
      applyStreamingSpecification(exchangeSpecification, privateStreamingService);
      streamingTradeService = new CryptoComStreamingTradeService(privateStreamingService);
      streamingAccountService =
          new CryptoComStreamingAccountService(privateStreamingService, eventDeduplicator);
      // Independent connections to different hosts - no reason to serialize them.
      privateConnect = privateStreamingService.connect();
    }

    return Completable.mergeArray(publicConnect, privateConnect)
        .doOnComplete(() -> subscribeRequestedChannels(subscription));
  }

  /** Subscribes the channels declared by the connect subscription on their owning transports. */
  private void subscribeRequestedChannels(ProductSubscription subscription) {
    if (subscription == null || subscription.isEmpty()) {
      return;
    }
    for (Instrument instrument : subscription.getOrderBook()) {
      subscribeChannel(
          "book."
              + CryptoComAdapters.toInstrumentName(instrument)
              + "."
              + DEFAULT_BOOK_DEPTH,
          true);
    }
    for (Instrument instrument : subscription.getTrades()) {
      subscribeChannel("trade." + CryptoComAdapters.toInstrumentName(instrument), true);
    }
    for (Instrument instrument : subscription.getTicker()) {
      subscribeChannel("ticker." + CryptoComAdapters.toInstrumentName(instrument), true);
    }
    for (Instrument instrument : subscription.getOrders()) {
      subscribeChannel("user.order." + CryptoComAdapters.toInstrumentName(instrument), false);
    }
    for (Instrument instrument : subscription.getUserTrades()) {
      subscribeChannel("user.trade." + CryptoComAdapters.toInstrumentName(instrument), false);
    }
    // user.balance is instrument-less on the wire; the subscription currencies select the
    // balances the caller is interested in, but the channel is subscribed once.
    if (!subscription.getBalances().isEmpty()) {
      subscribeChannel("user.balance", false);
    }
  }

  private void subscribeChannel(String channel, boolean publicTransport) {
    CryptoComStreamingService service =
        publicTransport ? publicStreamingService : privateStreamingService;
    if (service == null) {
      return;
    }
    Disposable disposable =
        service
            .subscribeChannel(channel)
            .subscribe(
                ignored -> {
                  // Subscription outcome is tracked by confirmation in the service.
                },
                error -> LOG.warn("Requested channel {} failed to subscribe: {}", channel, error));
    subscriptionDisposables.add(disposable);
  }

  /** Channel names the connect subscription requires to be active for {@link #isAlive()}. */
  static Set<String> channelsFor(ProductSubscription subscription) {
    Set<String> channels = new HashSet<>();
    for (Instrument instrument : subscription.getOrderBook()) {
      channels.add(
          "book."
              + CryptoComAdapters.toInstrumentName(instrument)
              + "."
              + DEFAULT_BOOK_DEPTH);
    }
    for (Instrument instrument : subscription.getTrades()) {
      channels.add("trade." + CryptoComAdapters.toInstrumentName(instrument));
    }
    for (Instrument instrument : subscription.getTicker()) {
      channels.add("ticker." + CryptoComAdapters.toInstrumentName(instrument));
    }
    for (Instrument instrument : subscription.getOrders()) {
      channels.add("user.order." + CryptoComAdapters.toInstrumentName(instrument));
    }
    for (Instrument instrument : subscription.getUserTrades()) {
      channels.add("user.trade." + CryptoComAdapters.toInstrumentName(instrument));
    }
    if (!subscription.getBalances().isEmpty()) {
      channels.add("user.balance");
    }
    return channels;
  }

  /**
   * True when the subscription passed to {@link #connect(ProductSubscription...)} needs the
   * private user transport; extracted for testability of the transport derivation.
   */
  static boolean privateTransportRequired(ProductSubscription subscription) {
    return subscription != null && subscription.hasAuthenticated();
  }

  String publicWsUrl(String overrideBaseUrl) {
    return overrideBaseUrl != null
        ? overrideBaseUrl + "/exchange/v1/market"
        : PUBLIC_WS_URL;
  }

  String privateWsUrl(String overrideBaseUrl) {
    return overrideBaseUrl != null ? overrideBaseUrl + "/exchange/v1/user" : PRIVATE_WS_URL;
  }

  /** The deduplicator used by the private event streams; {@code null} before connecting. */
  CryptoComStreamingEventDeduplicator eventDeduplicator() {
    return eventDeduplicator;
  }

  @Override
  public Completable disconnect() {
    List<Disposable> toDispose;
    synchronized (subscriptionDisposables) {
      toDispose = new ArrayList<>(subscriptionDisposables);
      subscriptionDisposables.clear();
    }
    toDispose.forEach(Disposable::dispose);

    Completable publicDisconnect =
        publicStreamingService == null
            ? Completable.complete()
            : publicStreamingService.disconnect();
    Completable privateDisconnect =
        privateStreamingService == null
            ? Completable.complete()
            : privateStreamingService.disconnect();

    privateRequired = false;
    requiredChannels.clear();
    servicesDisposed = true;

    return Completable.mergeArray(publicDisconnect, privateDisconnect);
  }

  /**
   * Aggregate liveness over every required transport: the public socket must be open on the
   * current connection generation, an opened private socket must be authenticated, and every
   * channel requested through the connect subscription must have been confirmed by the server.
   * A public-only subscription is alive without any private socket.
   */
  @Override
  public boolean isAlive() {
    return isAlive(
        publicStreamingService,
        privateStreamingService,
        privateRequired,
        requiredChannels);
  }

  /** Liveness decision over explicit transports; extracted for deterministic tests. */
  static boolean isAlive(
      CryptoComStreamingService publicStreamingService,
      CryptoComPrivateStreamingService privateStreamingService,
      boolean privateRequired,
      Set<String> requiredChannels) {
    if (publicStreamingService == null || !publicStreamingService.isSocketOpen()) {
      return false;
    }
    if (!publicStreamingService.isCurrentConnection()) {
      return false;
    }
    if (privateRequired) {
      if (privateStreamingService == null
          || !privateStreamingService.isSocketOpen()
          || !privateStreamingService.isCurrentConnection()) {
        return false;
      }
      if (!privateStreamingService.isAuthenticated()) {
        return false;
      }
    }
    for (String channel : requiredChannels) {
      if (publicStreamingService.isChannelActive(channel)
          || (privateStreamingService != null && privateStreamingService.isChannelActive(channel))) {
        continue;
      }
      return false;
    }
    return true;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (publicStreamingService != null) {
      publicStreamingService.useCompressedMessages(compressedMessages);
    }
    if (privateStreamingService != null) {
      privateStreamingService.useCompressedMessages(compressedMessages);
    }
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return servicesDisposed ? null : streamingMarketDataService;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return servicesDisposed ? null : streamingTradeService;
  }

  @Override
  public StreamingAccountService getStreamingAccountService() {
    return servicesDisposed ? null : streamingAccountService;
  }
}