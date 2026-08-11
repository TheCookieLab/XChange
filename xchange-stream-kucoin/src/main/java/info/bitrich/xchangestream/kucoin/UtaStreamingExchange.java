package info.bitrich.xchangestream.kucoin;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import info.bitrich.xchangestream.util.Events;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.knowm.xchange.kucoin.KucoinApiMode;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.dto.UtaTradeType;

/**
 * UTA-generation streaming exchange.
 *
 * <p>Non-blocking connect; public transport selection per trade type (SPOT
 * {@code wss://x-push-spot.kucoin.com}, FUTURES {@code wss://x-push-futures.kucoin.com}); private
 * transport on {@code wss://wsapi-push.kucoin.com} with a 24-hour token re-acquired on every
 * reconnect; aggregate liveness across every required transport (a single dead required socket
 * makes {@link #isAlive()} false); generation-aware reconnect and full resubscription via the
 * underlying service.
 */
public class UtaStreamingExchange extends KucoinExchange implements StreamingExchange {

  private static final String SPOT_PUSH_URI = "wss://x-push-spot.kucoin.com";
  private static final String FUTURES_PUSH_URI = "wss://x-push-futures.kucoin.com";
  private static final String PRIVATE_PUSH_URI = "wss://wsapi-push.kucoin.com";

  private final List<NettyStreamingService<?>> services = new ArrayList<>();
  private final AtomicBoolean connected = new AtomicBoolean();
  private UtaStreamingService publicSpotService;
  private UtaStreamingService publicFuturesService;
  private UtaStreamingService privateService;
  private UtaStreamingMarketDataService streamingMarketDataService;
  private UtaStreamingTradeService streamingTradeService;

  @Override
  protected void initServices() {
    super.initServices();
    Events.onApiCall(exchangeSpecification);
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    if (connected.get()) {
      return Completable.complete();
    }
    if (getApiMode() != KucoinApiMode.UTA) {
      return Completable.error(
          new IllegalStateException(
              "UtaStreamingExchange requires exchange parameter '"
                  + API_MODE_PARAMETER
                  + "' = UTA"));
    }

    ProductSubscription subscriptions = args[0];
    List<Completable> connects = new ArrayList<>();
    services.clear();

    if (subscriptions.hasUnauthenticated()) {
      java.util.Set<String> symbols = new java.util.HashSet<>();
      subscriptions.getOrderBook().forEach(p -> symbols.add(getUtaProviderSymbol(p)));
      subscriptions.getTicker().forEach(p -> symbols.add(getUtaProviderSymbol(p)));
      subscriptions.getTrades().forEach(p -> symbols.add(getUtaProviderSymbol(p)));
      boolean needsFutures = symbols.stream().anyMatch(s -> !s.contains("-"));
      boolean needsSpot = symbols.stream().anyMatch(s -> s.contains("-"));

      if (needsSpot) {
        publicSpotService = new UtaStreamingService(SPOT_PUSH_URI, false, null);
        applyStreamingSpecification(exchangeSpecification, publicSpotService);
        services.add(publicSpotService);
        connects.add(publicSpotService.connect());
      }
      if (needsFutures) {
        publicFuturesService = new UtaStreamingService(FUTURES_PUSH_URI, false, null);
        applyStreamingSpecification(exchangeSpecification, publicFuturesService);
        services.add(publicFuturesService);
        connects.add(publicFuturesService.connect());
      }
    }

    if (subscriptions.hasAuthenticated()) {
      if (exchangeSpecification.getApiKey() == null) {
        return Completable.error(
            new IllegalArgumentException("API key required for authenticated UTA streams"));
      }
      privateService =
          new UtaStreamingService(
              PRIVATE_PUSH_URI,
              true,
              () -> {
                try {
                  return getUtaAccountService().getPrivateWsToken().getToken();
                } catch (IOException e) {
                  throw new RuntimeException("Failed to refresh UTA private WS token", e);
                }
              });
      applyStreamingSpecification(exchangeSpecification, privateService);
      services.add(privateService);
      connects.add(privateService.connect());
    }

    return Completable
        .concat(connects)
        .doOnComplete(
            () -> {
              if (publicSpotService != null) {
                streamingMarketDataService =
                    new UtaStreamingMarketDataService(publicSpotService, this);
              } else if (publicFuturesService != null) {
                streamingMarketDataService =
                    new UtaStreamingMarketDataService(publicFuturesService, this);
              }
              if (privateService != null) {
                streamingTradeService = new UtaStreamingTradeService(privateService, this);
              }
              connected.set(true);
            });
  }

  @Override
  public Completable disconnect() {
    connected.set(false);
    if (publicSpotService != null) {
      publicSpotService = null;
    }
    if (publicFuturesService != null) {
      publicFuturesService = null;
    }
    if (privateService != null) {
      privateService = null;
    }
    streamingMarketDataService = null;
    streamingTradeService = null;

    List<Completable> completables =
        services.stream().map(NettyStreamingService::disconnect).collect(Collectors.toList());
    services.clear();
    return Completable.concat(completables);
  }

  @Override
  public boolean isAlive() {
    // Aggregate liveness: every required transport must be open.
    if (services.isEmpty()) {
      return false;
    }
    return services.stream().allMatch(NettyStreamingService::isSocketOpen);
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return Observable.concat(
        services.stream()
            .map(NettyStreamingService::subscribeReconnectFailure)
            .collect(Collectors.toList()));
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return Observable.concat(
        services.stream()
            .map(NettyStreamingService::subscribeConnectionSuccess)
            .collect(Collectors.toList()));
  }

  @Override
  public Observable<Object> disconnectObservable() {
    return Observable.concat(
        services.stream()
            .map(NettyStreamingService::subscribeDisconnect)
            .collect(Collectors.toList()));
  }

  @Override
  public UtaStreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return streamingTradeService;
  }

  public UtaStreamingTradeService getUtaStreamingTradeService() {
    return streamingTradeService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    services.forEach(s -> s.useCompressedMessages(compressedMessages));
  }

  /** @return the push-service trade type selector for a provider symbol */
  static String pushEndpointFor(String symbol) {
    return symbol != null && symbol.contains("-") ? SPOT_PUSH_URI : FUTURES_PUSH_URI;
  }
}
