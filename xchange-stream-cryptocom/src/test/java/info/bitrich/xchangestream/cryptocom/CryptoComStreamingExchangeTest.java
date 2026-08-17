package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/**
 * Behavioral tests for {@link CryptoComStreamingExchange}: fail-closed sandbox endpoint handling
 * (an unverified sandbox host is never defaulted; only an explicit caller-verified override opts
 * in), subscription-derived transport selection, channel derivation for the connect subscription,
 * and the transport-aggregated {@code isAlive()}. Deterministic fixtures only - no network or
 * live calls.
 */
public class CryptoComStreamingExchangeTest {

  /** Public service stub whose connection state tests can control. */
  static final class StubPublicService extends CryptoComStreamingService {

    boolean socketOpen;
    boolean currentConnection;
    final Set<String> active = new HashSet<>();

    StubPublicService() {
      super("wss://stream.crypto.com/exchange/v1/market");
    }

    @Override
    public boolean isSocketOpen() {
      return socketOpen;
    }

    @Override
    public boolean isCurrentConnection() {
      return currentConnection;
    }

    @Override
    public boolean isChannelActive(String channelName) {
      return active.contains(channelName);
    }
  }

  /** Private service stub; additionally exposes the authentication flag. */
  static final class StubPrivateService extends CryptoComPrivateStreamingService {

    boolean socketOpen;
    boolean currentConnection;
    boolean authenticated = true;
    final Set<String> active = new HashSet<>();

    StubPrivateService() {
      super("wss://stream.crypto.com/exchange/v1/user", "api-key", "api-secret");
    }

    @Override
    public boolean isSocketOpen() {
      return socketOpen;
    }

    @Override
    public boolean isCurrentConnection() {
      return currentConnection;
    }

    @Override
    public boolean isAuthenticated() {
      return authenticated;
    }

    @Override
    public boolean isChannelActive(String channelName) {
      return active.contains(channelName);
    }
  }

  @Test
  public void testSandboxWithoutOverrideFailsClosed() {
    // given
    CryptoComStreamingExchange exchange =
        (CryptoComStreamingExchange)
            ExchangeFactory.INSTANCE.createExchange(CryptoComStreamingExchange.class);
    exchange.getExchangeSpecification().setExchangeSpecificParametersItem(StreamingExchange.USE_SANDBOX, true);

    // when
    TestObserver<Void> observer = exchange.connect().test();

    // then: never connects to a guessed/unverified sandbox host
    assertThat(exchange.getStreamingMarketDataService()).isNull();
    final Throwable[] error = new Throwable[1];
    observer.assertError(t -> t instanceof ExchangeException && (error[0] = t) != null);
    assertThat(error[0].getMessage())
        .contains(CryptoComStreamingExchange.CRYPTOCOM_WS_OVERRIDE_URI);
  }

  @Test
  public void testAuthenticatedSubscriptionWithoutCredentialsFailsExplicitly() {
    // given
    CryptoComStreamingExchange exchange =
        (CryptoComStreamingExchange)
            ExchangeFactory.INSTANCE.createExchange(CryptoComStreamingExchange.class);
    ProductSubscription subscription =
        ProductSubscription.create()
            .addOrderbook(CurrencyPair.BTC_USDT)
            .addOrders(CurrencyPair.BTC_USDT)
            .build();

    // when
    TestObserver<Void> observer = exchange.connect(subscription).test();

    // then
    final Throwable[] error = new Throwable[1];
    observer.assertError(t -> t instanceof ExchangeSecurityException && (error[0] = t) != null);
    assertThat(error[0].getMessage()).contains("API credentials");
  }

  @Test
  public void testPublicOnlyReconnectDropsStalePrivateTransport() {
    // given: a previous authenticated connection left user-plane services wired to a private socket
    CryptoComStreamingExchange exchange =
        (CryptoComStreamingExchange)
            ExchangeFactory.INSTANCE.createExchange(CryptoComStreamingExchange.class);
    StubPrivateService stalePrivate = new StubPrivateService();
    exchange.privateStreamingService = stalePrivate;
    exchange.streamingTradeService = new CryptoComStreamingTradeService(stalePrivate);
    exchange.streamingAccountService =
        new CryptoComStreamingAccountService(
            stalePrivate, new CryptoComStreamingEventDeduplicator());

    // when: the next connect is public-only
    exchange.dropStalePrivateTransport();

    // then: no user-plane service survives on the stale socket
    assertThat(exchange.privateStreamingService).isNull();
    assertThat(exchange.streamingTradeService).isNull();
    assertThat(exchange.streamingAccountService).isNull();
    assertThat(exchange.getStreamingTradeService()).isNull();
    assertThat(exchange.getStreamingAccountService()).isNull();
  }

  @Test
  public void testPublicOnlySubscriptionNeedsNoPrivateTransport() {
    ProductSubscription subscription =
        ProductSubscription.create()
            .addTicker(CurrencyPair.BTC_USDT)
            .addOrderbook(CurrencyPair.BTC_USDT)
            .build();
    assertThat(CryptoComStreamingExchange.privateTransportRequired(subscription)).isFalse();
    assertThat(CryptoComStreamingExchange.privateTransportRequired(null)).isFalse();
  }

  @Test
  public void testAuthenticatedChannelsRequirePrivateTransport() {
    ProductSubscription subscription =
        ProductSubscription.create().addOrders(CurrencyPair.BTC_USDT).build();
    assertThat(CryptoComStreamingExchange.privateTransportRequired(subscription)).isTrue();

    ProductSubscription userTrades =
        ProductSubscription.create().addUserTrades(CurrencyPair.BTC_USDT).build();
    assertThat(CryptoComStreamingExchange.privateTransportRequired(userTrades)).isTrue();

    ProductSubscription balances =
        ProductSubscription.create().addBalances(Currency.USDT).build();
    assertThat(CryptoComStreamingExchange.privateTransportRequired(balances)).isTrue();
  }

  @Test
  public void testChannelsForDerivesAllRequestedChannelsWithDefaultBookDepth() {
    // given
    ProductSubscription subscription =
        ProductSubscription.create()
            .addOrderbook(CurrencyPair.BTC_USDT)
            .addTrades(CurrencyPair.ETH_USDT)
            .addTicker(CurrencyPair.BTC_USDT)
            .addOrders(CurrencyPair.BTC_USDT)
            .addUserTrades(CurrencyPair.ETH_USDT)
            .addBalances(Currency.USD)
            .addBalances(Currency.USDT)
            .build();

    // when
    Set<String> channels = CryptoComStreamingExchange.channelsFor(subscription);

    // then
    assertThat(channels)
        .containsExactlyInAnyOrder(
            "book.BTC_USDT.10",
            "trade.ETH_USDT",
            "ticker.BTC_USDT",
            "user.order.BTC_USDT",
            "user.trade.ETH_USDT",
            "user.balance");
  }

  @Test
  public void testEndpointResolutionUsesOverrideBaseUrlOnlyWhenProvided() {
    CryptoComStreamingExchange exchange =
        (CryptoComStreamingExchange)
            ExchangeFactory.INSTANCE.createExchange(CryptoComStreamingExchange.class);

    assertThat(exchange.publicWsUrl(null)).isEqualTo(CryptoComStreamingExchange.PUBLIC_WS_URL);
    assertThat(exchange.privateWsUrl(null)).isEqualTo(CryptoComStreamingExchange.PRIVATE_WS_URL);
    assertThat(exchange.publicWsUrl("wss://uat.example.com"))
        .isEqualTo("wss://uat.example.com/exchange/v1/market");
    assertThat(exchange.privateWsUrl("wss://uat.example.com"))
        .isEqualTo("wss://uat.example.com/exchange/v1/user");
  }

  @Test
  public void testIsAliveAggregatesPublicSocketAndGeneration() {
    StubPublicService publicService = new StubPublicService();
    Set<String> channels = new HashSet<>();
    channels.add("book.BTC_USDT.10");

    // not open -> dead
    assertThat(CryptoComStreamingExchange.isAlive(publicService, null, false, channels)).isFalse();

    // open but stale generation -> dead
    publicService.socketOpen = true;
    assertThat(CryptoComStreamingExchange.isAlive(publicService, null, false, channels)).isFalse();

    // open and current generation but requested channel not confirmed -> dead
    publicService.currentConnection = true;
    assertThat(CryptoComStreamingExchange.isAlive(publicService, null, false, channels)).isFalse();

    // everything confirmed -> alive
    publicService.active.add("book.BTC_USDT.10");
    assertThat(CryptoComStreamingExchange.isAlive(publicService, null, false, channels)).isTrue();
  }

  @Test
  public void testIsAliveRequiresPrivateSocketAndAuthWhenPrivateRequired() {
    StubPublicService publicService = new StubPublicService();
    publicService.socketOpen = true;
    publicService.currentConnection = true;
    StubPrivateService privateService = new StubPrivateService();
    Set<String> channels = new HashSet<>();

    // private transport never created -> dead
    assertThat(CryptoComStreamingExchange.isAlive(publicService, null, true, channels)).isFalse();

    // private open + current but not authenticated -> dead
    privateService.socketOpen = true;
    privateService.currentConnection = true;
    privateService.authenticated = false;
    assertThat(CryptoComStreamingExchange.isAlive(publicService, privateService, true, channels))
        .isFalse();

    // authenticated -> alive, and a public-only subscription was alive without any private socket
    privateService.authenticated = true;
    assertThat(CryptoComStreamingExchange.isAlive(publicService, privateService, true, channels))
        .isTrue();
  }

  @Test
  public void testIsAliveChecksPrivateChannelsOnThePrivateTransport() {
    StubPublicService publicService = new StubPublicService();
    publicService.socketOpen = true;
    publicService.currentConnection = true;
    StubPrivateService privateService = new StubPrivateService();
    privateService.socketOpen = true;
    privateService.currentConnection = true;
    Set<String> channels = new HashSet<>();
    channels.add("user.order.BTC_USDT");

    assertThat(CryptoComStreamingExchange.isAlive(publicService, privateService, true, channels))
        .isFalse();
    privateService.active.add("user.order.BTC_USDT");
    assertThat(CryptoComStreamingExchange.isAlive(publicService, privateService, true, channels))
        .isTrue();
  }
}