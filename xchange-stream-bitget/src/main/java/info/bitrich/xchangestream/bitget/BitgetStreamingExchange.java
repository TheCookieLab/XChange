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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.ExchangeSpecification;
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

  /**
   * The currently wired transport/wrapper holder, updated atomically so a disconnect completing
   * after a concurrent connect installed a fresh holder can only clear the holder it actually
   * shut down (compare-and-set) instead of wiping the newer generation.
   */
  private final AtomicReference<StreamingServices> services =
      new AtomicReference<>(StreamingServices.EMPTY);

  /**
   * The classic-mode public transport. Return type and descriptor are preserved for binary
   * compatibility with clients compiled against the original Lombok getter; in UTA V3 mode the
   * public socket is a different service type, so this accessor yields null and callers use
   * {@link #getPublicNettyStreamingService()} instead.
   */
  public BitgetStreamingService getPublicStreamingService() {
    return services.get().publicService instanceof BitgetStreamingService
        ? (BitgetStreamingService) services.get().publicService
        : null;
  }

  /**
   * The classic-mode private transport. Return type and descriptor are preserved for binary
   * compatibility with clients compiled against the original Lombok getter; in UTA V3 mode the
   * private socket is a different service type, so this accessor yields null and callers use
   * {@link #getPrivateNettyStreamingService()} instead.
   */
  public BitgetPrivateStreamingService getPrivateStreamingService() {
    return services.get().privateService instanceof BitgetPrivateStreamingService
        ? (BitgetPrivateStreamingService) services.get().privateService
        : null;
  }

  /** The currently wired public transport, regardless of API mode. */
  public NettyStreamingService<?> getPublicNettyStreamingService() {
    return services.get().publicService;
  }

  /** The currently wired private transport, regardless of API mode. */
  public NettyStreamingService<?> getPrivateNettyStreamingService() {
    return services.get().privateService;
  }

  public StreamingMarketDataService getStreamingMarketDataService() {
    return services.get().marketDataService;
  }

  public StreamingTradeService getStreamingTradeService() {
    return services.get().tradeService;
  }

  public StreamingAccountService getStreamingAccountService() {
    return services.get().accountService;
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
    // cold composition, same rationale as connectUtaV3(): the exchange holder must keep serving
    // the live transports until the connect subscription begins, so the standard reconnect idiom
    // disconnect().andThen(connect()) shuts down the OLD sockets and only then wires the new
    // ones. Eagerly constructing and assigning services here would replace the holder while the
    // disconnect() Completable is still unsubscribed, and disconnect() would then shut down the
    // freshly created unopened transports instead of the live sockets, orphaning them.
    return Completable.defer(
        () -> {
          BitgetStreamingService publicService = createClassicPublicService(exchangeSpecification);
          BitgetPrivateStreamingService privateService = null;
          StreamingTradeService tradeService = null;
          if (StringUtils.isNoneBlank(
              exchangeSpecification.getApiKey(),
              exchangeSpecification.getSecretKey(),
              exchangeSpecification.getPassword())) {
            privateService = createClassicPrivateService(exchangeSpecification);
            tradeService = new BitgetStreamingTradeService(privateService);
          }
          applyStreamingSpecification(exchangeSpecification, publicService);
          // install the holder BEFORE awaiting the private connection: NettyStreamingService
          // schedules automatic reconnection when the handshake fails, and a private transport
          // that is not yet reachable through the holder could never be stopped by disconnect(),
          // leaking its event loop and reconnect attempts
          services.set(
              new StreamingServices(
                  publicService,
                  privateService,
                  new BitgetStreamingMarketDataService(publicService),
                  tradeService,
                  null));
          if (privateService != null) {
            BitgetPrivateStreamingService connectedPrivateService = privateService;
            // a failing private connection is disconnected before the error propagates:
            // NettyStreamingService schedules an automatic retry when the handshake fails, and
            // cancelling it here prevents a later success beside a never-opened public transport
            // (isAlive() false, public streams unavailable) until the caller reconnects
            connectedPrivateService
                .connect()
                .onErrorResumeNext(
                    error ->
                        connectedPrivateService
                            .disconnect()
                            .andThen(Completable.error(error)))
                .blockingAwait();
          }

          if (privateService == null) {
            return publicService.connect();
          }
          BitgetPrivateStreamingService connectedPrivateService = privateService;
          return publicService
              .connect()
              .onErrorResumeNext(
                  error ->
                      // disconnect BOTH transports: the public failure schedules its own
                      // NettyStreamingService retry, and if it later succeeded beside a closed
                      // private socket the holder would report isAlive() false with private
                      // streams unrecoverable until the caller reconnects
                      connectedPrivateService
                          .disconnect()
                          .andThen(publicService.disconnect())
                          .andThen(Completable.error(error)));
        });
  }

  /**
   * Creates the classic V2 public transport.
   *
   * <p>Protected so tests can substitute a transport whose connection outcome is deterministic
   * (a failed public connection is disconnected together with the private transport so its
   * automatic retry cannot outlive the failed aggregate connect).
   */
  protected BitgetStreamingService createClassicPublicService(ExchangeSpecification specification) {
    return new BitgetStreamingService(Config.V2_PUBLIC_WS_URL);
  }

  /**
   * Creates the classic V2 private transport for the configured credentials.
   *
   * <p>Protected so tests can substitute a transport whose connection outcome is deterministic
   * (the classic path awaits the private connection with {@code blockingAwait()}).
   */
  protected BitgetPrivateStreamingService createClassicPrivateService(
      ExchangeSpecification specification) {
    return new BitgetPrivateStreamingService(
        Config.V2_PRIVATE_WS_URL,
        specification.getApiKey(),
        specification.getSecretKey(),
        specification.getPassword());
  }

  /**
   * Creates the UTA V3 public transport.
   *
   * <p>Protected so tests can substitute a transport whose connection outcome is deterministic
   * (a failed public connection is disconnected together with the private transport so its
   * automatic retry cannot outlive the failed aggregate connect).
   */
  protected BitgetUtaV3StreamingService createUtaV3PublicService(
      ExchangeSpecification specification) {
    return new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL);
  }

  /**
   * Creates the UTA V3 private transport for the configured credentials.
   *
   * <p>Protected so tests can substitute a transport whose connection outcome is deterministic
   * (the UTA V3 path cancels the private transport when the aggregate connect fails, so its
   * automatic reconnect cannot outlive the failed attempt and orphan the unopened public socket).
   */
  protected BitgetUtaV3PrivateStreamingService createUtaV3PrivateService(
      ExchangeSpecification specification) {
    return new BitgetUtaV3PrivateStreamingService(
        Config.V3_PRIVATE_WS_URL,
        specification.getApiKey(),
        specification.getSecretKey(),
        specification.getPassword());
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
              createUtaV3PublicService(exchangeSpecification);
          applyStreamingSpecification(exchangeSpecification, publicService);
          StreamingMarketDataService marketDataService =
              new BitgetUtaV3StreamingMarketDataService(publicService);
          if (StringUtils.isNoneBlank(
              exchangeSpecification.getApiKey(),
              exchangeSpecification.getSecretKey(),
              exchangeSpecification.getPassword())) {
            BitgetUtaV3PrivateStreamingService privateService =
                createUtaV3PrivateService(exchangeSpecification);
            // private channels must honor the same proxy/cert/connection-hook/auto-reconnect
            // settings as the public socket
            applyStreamingSpecification(exchangeSpecification, privateService);
            services.set(
                new StreamingServices(
                    publicService,
                    privateService,
                    marketDataService,
                    new BitgetUtaV3StreamingTradeService(
                        privateService, (BitgetUtaV3TradeService) getTradeService()),
                    new BitgetUtaV3StreamingAccountService(privateService)));
            // a failing private connection must cancel the private transport's own automatic
            // reconnect: if it were left to retry and later succeeded, the aggregate connect has
            // already errored, the public socket was never opened, and the holder would carry a
            // live private socket beside an unopened public one (isAlive() false, public streams
            // unavailable) until the caller manually reconnects
            return privateService
                .connect()
                .onErrorResumeNext(
                    error -> privateService.disconnect().andThen(Completable.error(error)))
                .andThen(
                    publicService
                        .connect()
                        .onErrorResumeNext(
                            error ->
                                // a failed public connection schedules its own automatic retry,
                                // so disconnect BOTH transports: a later public success beside the
                                // closed private socket would keep isAlive() false with private
                                // streams unrecoverable until the caller reconnects
                                privateService
                                    .disconnect()
                                    .andThen(publicService.disconnect())
                                    .andThen(Completable.error(error))));
          }
          services.set(new StreamingServices(publicService, null, marketDataService, null, null));
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
          StreamingServices current = services.get();
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
          // socket; connect() rebuilds every field from scratch. Only clear when the holder still
          // refers to the transport being shut down: if a concurrent connect() already installed a
          // fresh holder while this disconnect was completing, wiping it would orphan the new live
          // sockets (reachable through no getter, never shut down by a later disconnect).
          return transportDisconnect.doOnComplete(
              // atomic: if a concurrent connect() already installed a fresh holder, this only
              // clears the holder that was actually shut down, never the newer generation
              () -> services.compareAndSet(current, StreamingServices.EMPTY));
        });
  }

  @Override
  public boolean isAlive() {
    if (services.get().publicService == null) {
      return false;
    }
    if (services.get().privateService == null) {
      return services.get().publicService.isSocketOpen();
    }
    return services.get().publicService.isSocketOpen() && services.get().privateService.isSocketOpen();
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (services.get().publicService != null) {
      services.get().publicService.useCompressedMessages(compressedMessages);
    }
    if (services.get().privateService != null) {
      services.get().privateService.useCompressedMessages(compressedMessages);
    }
  }
}
