package info.bitrich.xchangestream.bitget;

import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3PrivateStreamingService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingAccountService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingMarketDataService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingTradeService;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.reactivex.rxjava3.core.Completable;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;

public class BitgetStreamingExchange extends BitgetExchange implements StreamingExchange {

  /** Immutable snapshot of the transport and wrapper services currently wired to this exchange. */
  private static final class StreamingServices {
    private static final StreamingServices EMPTY = new StreamingServices(null, null, null, null, null);

    private final NettyStreamingService<?> publicService;
    private final NettyStreamingService<?> privateService;
    private final StreamingMarketDataService marketDataService;
    private final StreamingTradeService tradeService;
    private final StreamingAccountService accountService;

    private StreamingServices(
        NettyStreamingService<?> publicService,
        NettyStreamingService<?> privateService,
        StreamingMarketDataService marketDataService,
        StreamingTradeService tradeService,
        StreamingAccountService accountService) {
      this.publicService = publicService;
      this.privateService = privateService;
      this.marketDataService = marketDataService;
      this.tradeService = tradeService;
      this.accountService = accountService;
    }

    private boolean isEmpty() {
      return publicService == null && privateService == null;
    }
  }

  private volatile StreamingServices services = StreamingServices.EMPTY;

  /**
   * The classic-mode public transport. Return type and descriptor are preserved for binary
   * compatibility with clients compiled against the original Lombok getter; in UTA V3 mode the
   * public socket is a different service type, so this accessor yields null and callers use
   * {@link #getPublicNettyStreamingService()} instead.
   */
  public BitgetStreamingService getPublicStreamingService() {
    return services.publicService instanceof BitgetStreamingService
        ? (BitgetStreamingService) services.publicService
        : null;
  }

  /**
   * The classic-mode private transport. Return type and descriptor are preserved for binary
   * compatibility with clients compiled against the original Lombok getter; in UTA V3 mode the
   * private socket is a different service type, so this accessor yields null and callers use
   * {@link #getPrivateNettyStreamingService()} instead.
   */
  public BitgetPrivateStreamingService getPrivateStreamingService() {
    return services.privateService instanceof BitgetPrivateStreamingService
        ? (BitgetPrivateStreamingService) services.privateService
        : null;
  }

  /** The currently wired public transport, regardless of API mode. */
  public NettyStreamingService<?> getPublicNettyStreamingService() {
    return services.publicService;
  }

  /** The currently wired private transport, regardless of API mode. */
  public NettyStreamingService<?> getPrivateNettyStreamingService() {
    return services.privateService;
  }

  public StreamingMarketDataService getStreamingMarketDataService() {
    return services.marketDataService;
  }

  public StreamingTradeService getStreamingTradeService() {
    return services.tradeService;
  }

  public StreamingAccountService getStreamingAccountService() {
    return services.accountService;
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    switch (getApiMode()) {
      case CLASSIC_V2:
        return connectClassicV2();
      case UTA_V3:
        return connectUtaV3();
      default:
        throw new IllegalStateException("Unknown Bitget API mode: " + getApiMode());
    }
  }

  private Completable connectClassicV2() {
    BitgetStreamingService publicService = new BitgetStreamingService(Config.V2_PUBLIC_WS_URL);
    BitgetPrivateStreamingService privateService = null;
    StreamingTradeService tradeService = null;
    if (StringUtils.isNoneBlank(
        exchangeSpecification.getApiKey(),
        exchangeSpecification.getSecretKey(),
        exchangeSpecification.getPassword())) {
      privateService =
          new BitgetPrivateStreamingService(
              Config.V2_PRIVATE_WS_URL,
              exchangeSpecification.getApiKey(),
              exchangeSpecification.getSecretKey(),
              exchangeSpecification.getPassword());
      tradeService = new BitgetStreamingTradeService(privateService);
      privateService.connect().blockingAwait();
    }
    applyStreamingSpecification(exchangeSpecification, publicService);
    services =
        new StreamingServices(
            publicService,
            privateService,
            new BitgetStreamingMarketDataService(publicService),
            tradeService,
            null);

    if (privateService == null) {
      return publicService.connect();
    }
    BitgetPrivateStreamingService connectedPrivateService = privateService;
    return publicService
        .connect()
        .onErrorResumeNext(
            error ->
                connectedPrivateService.disconnect().andThen(Completable.error(error)));
  }

  private Completable connectUtaV3() {
    // cold composition: the exchange holder must keep serving the live transports until the
    // connect subscription begins, so the standard reconnect idiom
    // disconnect().andThen(connect()) shuts down the OLD sockets and only then wires the new
    // ones. Eagerly constructing and assigning services here would replace the holder while the
    // disconnect() Completable is still unsubscribed, and disconnect() would then shut down the
    // freshly created unopened transports instead of the live sockets, orphaning them.
    return Completable.defer(
        () -> {
          BitgetUtaV3StreamingService publicService =
              new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL);
          applyStreamingSpecification(exchangeSpecification, publicService);
          StreamingMarketDataService marketDataService =
              new BitgetUtaV3StreamingMarketDataService(publicService);
          if (StringUtils.isNoneBlank(
              exchangeSpecification.getApiKey(),
              exchangeSpecification.getSecretKey(),
              exchangeSpecification.getPassword())) {
            BitgetUtaV3PrivateStreamingService privateService =
                new BitgetUtaV3PrivateStreamingService(
                    Config.V3_PRIVATE_WS_URL,
                    exchangeSpecification.getApiKey(),
                    exchangeSpecification.getSecretKey(),
                    exchangeSpecification.getPassword());
            // private channels must honor the same proxy/cert/connection-hook/auto-reconnect
            // settings as the public socket
            applyStreamingSpecification(exchangeSpecification, privateService);
            services =
                new StreamingServices(
                    publicService,
                    privateService,
                    marketDataService,
                    new BitgetUtaV3StreamingTradeService(
                        privateService, (BitgetUtaV3TradeService) getTradeService()),
                    new BitgetUtaV3StreamingAccountService(privateService));
            return privateService
                .connect()
                .andThen(
                    publicService
                        .connect()
                        .onErrorResumeNext(
                            error ->
                                privateService
                                    .disconnect()
                                    .andThen(Completable.error(error))));
          }
          services = new StreamingServices(publicService, null, marketDataService, null, null);
          return publicService.connect();
        });
  }

  @Override
  public Completable disconnect() {
    // cold: resolve the wired services at subscription time and clear them only after the
    // transport shutdown completes, so a caller that composes the Completable without subscribing
    // keeps the references (and can retry a failed disconnect) instead of losing them early
    return Completable.defer(
        () -> {
          StreamingServices current = services;
          if (current.isEmpty()) {
            return Completable.complete();
          }
          Completable transportDisconnect =
              current.publicService == null
                  ? current.privateService.disconnect()
                  : current.privateService == null
                      ? current.publicService.disconnect()
                      : current.privateService.disconnect().andThen(current.publicService.disconnect());
          // drop the transport and wrapper fields so a later reconnect (possibly with a different
          // specification, e.g. without credentials) cannot observe or leak the closed private
          // socket; connect() rebuilds every field from scratch
          return transportDisconnect.doOnComplete(() -> services = StreamingServices.EMPTY);
        });
  }

  @Override
  public boolean isAlive() {
    if (services.publicService == null) {
      return false;
    }
    if (services.privateService == null) {
      return services.publicService.isSocketOpen();
    }
    return services.publicService.isSocketOpen() && services.privateService.isSocketOpen();
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (services.publicService != null) {
      services.publicService.useCompressedMessages(compressedMessages);
    }
    if (services.privateService != null) {
      services.privateService.useCompressedMessages(compressedMessages);
    }
  }
}
