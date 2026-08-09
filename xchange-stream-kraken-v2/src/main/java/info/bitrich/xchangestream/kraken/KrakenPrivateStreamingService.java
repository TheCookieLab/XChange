package info.bitrich.xchangestream.kraken;

import info.bitrich.xchangestream.kraken.config.Config;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.utils.ArrayUtils;
import org.knowm.xchange.utils.nonce.CurrentTimeIncrementalNonceFactory;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Authenticated Kraken Spot WebSocket v2 service.
 *
 * <p>Private channels require a REST-issued websocket token (lifetime 15 minutes). Tokens are
 * fetched single-flight: concurrent subscribers share one in-flight refresh instead of issuing
 * parallel REST calls, and the cached token is reused until it is close to expiring. Because the
 * token is cached per service instance and a new private service instance is created for each
 * exchange connect generation, a reconnect naturally re-authenticates with a fresh token.
 */
public class KrakenPrivateStreamingService extends KrakenStreamingService {

  /** Refresh well before the provider's 15-minute token lifetime. */
  static final long TOKEN_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

  protected KrakenStreamingExchange krakenStreamingExchange;
  protected KrakenAuthenticated krakenAuthenticated;
  protected ParamsDigest signatureCreator;

  private final SynchronizedValueFactory<Long> nonceFactory =
      new CurrentTimeIncrementalNonceFactory(TimeUnit.MILLISECONDS);

  private volatile String cachedToken;
  private volatile long tokenFetchedAtMillis;

  /** Test seam: rewinds the token fetch time to force a refresh on the next subscribe. */
  void setTokenFetchedAtMillis(long tokenFetchedAtMillis) {
    this.tokenFetchedAtMillis = tokenFetchedAtMillis;
  }

  public KrakenPrivateStreamingService(String apiUri, KrakenStreamingExchange exchange) {
    super(apiUri);

    krakenAuthenticated =
        ExchangeRestProxyBuilder.forInterface(
                KrakenAuthenticated.class, exchange.getExchangeSpecification())
            .build();
    signatureCreator =
        KrakenDigest.createInstance(exchange.getExchangeSpecification().getSecretKey());

    krakenStreamingExchange = exchange;
  }

  KrakenPrivateStreamingService(
      String apiUri,
      KrakenStreamingExchange exchange,
      KrakenAuthenticated krakenAuthenticated,
      ParamsDigest signatureCreator) {
    super(apiUri);
    this.krakenStreamingExchange = exchange;
    this.krakenAuthenticated = krakenAuthenticated;
    this.signatureCreator = signatureCreator;
  }

  /**
   * @return subscribe message containing a websocket token needed for private channels
   */
  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    CurrencyPair currencyPair = ArrayUtils.getElement(0, args, CurrencyPair.class, null);
    var message = KrakenStreamingAdapters.toSubscribeMessage(channelName, currencyPair);

    // get token for private channels
    if (Config.PRIVATE_CHANNELS.contains(channelName)) {
      message.getParams().setToken(getWebsocketToken());
    }
    return objectMapper.writeValueAsString(message);
  }

  /**
   * @return unsubscribe message containing a websocket token needed for private channels
   */
  @Override
  public String getUnsubscribeMessage(String subscriptionUniqueId, Object... args)
      throws IOException {
    var message = KrakenStreamingAdapters.toUnsubscribeMessage(subscriptionUniqueId);

    // get token for private channels
    if (Config.PRIVATE_CHANNELS.contains(message.getParams().getChannel())) {
      message.getParams().setToken(getWebsocketToken());
    }
    return objectMapper.writeValueAsString(message);
  }

  /**
   * Returns a fresh-enough websocket token, fetching it from REST at most once per TTL window.
   *
   * <p>Single-flight: concurrent callers wait on the monitor and reuse the token fetched by the
   * first caller, so N simultaneous subscribes never trigger more than one REST call.
   */
  private String getWebsocketToken() throws IOException {
    synchronized (this) {
      long now = System.currentTimeMillis();
      if (cachedToken != null && now - tokenFetchedAtMillis < TOKEN_TTL_MILLIS) {
        return cachedToken;
      }
      var tokenResult =
          krakenAuthenticated.getWebsocketToken(
              krakenStreamingExchange.getExchangeSpecification().getApiKey(),
              signatureCreator,
              nonceFactory);
      cachedToken = tokenResult.getResult().getToken();
      tokenFetchedAtMillis = now;
      return cachedToken;
    }
  }
}
