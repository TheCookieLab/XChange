package info.bitrich.xchangestream.kraken;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Completable;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;

/**
 * Kraken Spot WebSocket v2 exchange.
 *
 * <p>Lifecycle: the private (authenticated) socket is conditional — it is only created and
 * connected when API credentials are present in the {@link ExchangeSpecification}. Without
 * credentials, {@link #getStreamingTradeService()} and {@link #getStreamingAccountService()} return
 * {@code null} and only the public socket is used. Each successful {@link #connect()} bumps the
 * per-socket generation; the previously connected sockets are disconnected so stale generations
 * cannot deliver events after a re-connect. Service references are kept after {@link #disconnect()}
 * (sockets are closed, {@link #isAlive()} is {@code false}, and the streaming getters return {@code
 * null}); a later {@link #connect()} replaces them with fresh sockets.
 */
@Getter
public class KrakenStreamingExchange extends BaseExchange implements StreamingExchange {

  private final AtomicLong publicGeneration = new AtomicLong();
  private final AtomicLong privateGeneration = new AtomicLong();

  private volatile boolean connected;
  private KrakenStreamingService krakenStreamingService;
  private KrakenPrivateStreamingService krakenPrivateStreamingService;
  private StreamingMarketDataService streamingMarketDataService;
  private StreamingTradeService streamingTradeService;
  private StreamingAccountService streamingAccountService;

  @Override
  public Completable connect(ProductSubscription... args) {
    boolean privateRequired = privateSocketRequired();

    KrakenPrivateStreamingService previousPrivate = krakenPrivateStreamingService;
    if (privateRequired) {
      krakenPrivateStreamingService = createPrivateService();
      streamingTradeService = new KrakenStreamingTradeService(krakenPrivateStreamingService);
      streamingAccountService = new KrakenStreamingAccountService(krakenPrivateStreamingService);
      privateGeneration.incrementAndGet();
    } else if (previousPrivate != null) {
      // credentials dropped since the last connect: retire the stale private socket
      previousPrivate.disconnect().subscribe();
    }

    KrakenStreamingService previousPublic = krakenStreamingService;
    krakenStreamingService = createPublicService();
    publicGeneration.incrementAndGet();

    applyStreamingSpecification(exchangeSpecification, krakenStreamingService);
    streamingMarketDataService = new KrakenStreamingMarketDataService(krakenStreamingService);

    // drop the stale generation sockets so they cannot deliver events after this connect
    if (previousPrivate != null && previousPrivate != krakenPrivateStreamingService) {
      previousPrivate.disconnect().subscribe();
    }
    if (previousPublic != null && previousPublic != krakenStreamingService) {
      previousPublic.disconnect().subscribe();
    }

    if (privateRequired) {
      krakenPrivateStreamingService.connect().blockingAwait();
    }

    connected = true;
    return krakenStreamingService.connect();
  }

  /**
   * @return whether the authenticated private socket is required, i.e. API credentials are
   *     configured
   */
  public boolean privateSocketRequired() {
    return exchangeSpecification.getApiKey() != null
        && !exchangeSpecification.getApiKey().isEmpty()
        && exchangeSpecification.getSecretKey() != null
        && !exchangeSpecification.getSecretKey().isEmpty();
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    var specification = new ExchangeSpecification(getClass());
    specification.setExchangeName("Kraken");
    specification.setSslUri("https://api.kraken.com");
    specification.setOverrideWebsocketApiUri("wss://ws.kraken.com/v2");
    specification.setExchangeSpecificParametersItem(
        "V2_PRIVATE_WS_URL", "wss://ws-auth.kraken.com/v2");
    specification.setShouldLoadRemoteMetaData(false);
    return specification;
  }

  @Override
  public Completable disconnect() {
    connected = false;
    Completable publicDisconnect =
        krakenStreamingService != null
            ? krakenStreamingService.disconnect()
            : Completable.complete();
    if (krakenPrivateStreamingService != null) {
      return publicDisconnect.andThen(krakenPrivateStreamingService.disconnect());
    }
    return publicDisconnect;
  }

  @Override
  public boolean isAlive() {
    if (!connected || krakenStreamingService == null || !krakenStreamingService.isSocketOpen()) {
      return false;
    }
    if (privateSocketRequired()) {
      return krakenPrivateStreamingService != null && krakenPrivateStreamingService.isSocketOpen();
    }
    return true;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (krakenStreamingService != null) {
      krakenStreamingService.useCompressedMessages(compressedMessages);
    }
  }

  /**
   * @return the generation of the currently connected public socket, starting at 1
   */
  public long getPublicGeneration() {
    return publicGeneration.get();
  }

  /**
   * @return the generation of the currently connected private socket, starting at 1
   */
  public long getPrivateGeneration() {
    return privateGeneration.get();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return connected ? streamingMarketDataService : null;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return connected && privateSocketRequired() ? streamingTradeService : null;
  }

  @Override
  public StreamingAccountService getStreamingAccountService() {
    return connected && privateSocketRequired() ? streamingAccountService : null;
  }

  @Override
  protected void initServices() {}

  /** Creates the public streaming service; overridable for deterministic tests. */
  protected KrakenStreamingService createPublicService() {
    return new KrakenStreamingService(exchangeSpecification.getOverrideWebsocketApiUri());
  }

  /** Creates the private streaming service; overridable for deterministic tests. */
  protected KrakenPrivateStreamingService createPrivateService() {
    return new KrakenPrivateStreamingService(
        (String) exchangeSpecification.getParameter("V2_PRIVATE_WS_URL"), this);
  }
}
