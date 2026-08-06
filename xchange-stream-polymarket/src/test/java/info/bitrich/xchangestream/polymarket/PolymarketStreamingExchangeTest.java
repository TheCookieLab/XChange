package info.bitrich.xchangestream.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;

/**
 * Lifecycle tests for {@link PolymarketStreamingExchange}: public-only mode must never create,
 * connect, health-check, or reconnect an unusable user socket, while authenticated mode includes
 * the user socket in every lifecycle path — all without a live WebSocket.
 */
class PolymarketStreamingExchangeTest {

  private static final String API_KEY = "polymarket-l2-api-key";
  private static final String SECRET = "polymarket-l2-secret";
  private static final String PASSPHRASE = "polymarket-l2-passphrase";

  /** Records lifecycle calls and fakes socket state instead of opening a WebSocket. */
  private static final class FakeService extends PolymarketStreamingService {
    private final String apiKey;
    private final String secret;
    private final String passphrase;
    private final List<String> calls = new ArrayList<>();
    private boolean open;

    FakeService(String apiKey, String secret, String passphrase) {
      super("wss://stream.test/ws/channel", apiKey, secret, passphrase);
      this.apiKey = apiKey;
      this.secret = secret;
      this.passphrase = passphrase;
    }

    @Override
    public Completable connect() {
      calls.add("connect");
      open = true;
      return Completable.complete();
    }

    @Override
    public Completable disconnect() {
      calls.add("disconnect");
      open = false;
      return Completable.complete();
    }

    @Override
    public boolean isSocketOpen() {
      return open;
    }

    @Override
    public Observable<Throwable> subscribeReconnectFailure() {
      return Observable.never();
    }

    @Override
    public Observable<Object> subscribeConnectionSuccess() {
      return Observable.never();
    }

    @Override
    public Observable<Object> subscribeDisconnect() {
      return Observable.never();
    }
  }

  /** Exchange with an injectable service factory so lifecycle calls need no socket. */
  private static final class TestExchange extends PolymarketStreamingExchange {
    private final List<FakeService> services = new ArrayList<>();

    @Override
    protected PolymarketStreamingService createStreamingService(
        String apiUrl, String apiKey, String secret, String passphrase) {
      FakeService service = new FakeService(apiKey, secret, passphrase);
      services.add(service);
      return service;
    }
  }

  private static TestExchange exchange(boolean authenticated) {
    TestExchange exchange = new TestExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    if (authenticated) {
      spec.setApiKey(API_KEY);
      spec.setSecretKey(SECRET);
      spec.setPassword(PASSPHRASE);
    }
    exchange.applySpecification(spec);
    return exchange;
  }

  @Test
  void publicOnlyModeNeverCreatesOrConnectsTheUserService() {
    TestExchange exchange = exchange(false);

    assertEquals(1, exchange.services.size(), "no user service without the credential triplet");
    assertNull(
        exchange.getStreamingTradeService(),
        "public-only mode exposes no user trade service");
    assertNotNull(exchange.getStreamingMarketDataService());
    assertFalse(exchange.isAlive(), "nothing is connected yet");

    exchange.connect().blockingAwait();
    FakeService market = exchange.services.get(0);
    assertEquals(List.of("connect"), market.calls);
    assertTrue(exchange.isAlive(), "the market socket alone drives health in public-only mode");

    exchange.useCompressedMessages(true);
    exchange.disconnect().blockingAwait();
    assertFalse(exchange.isAlive());
    assertEquals(List.of("connect", "disconnect"), market.calls);

    // Lifecycle observables must work with a single service, not NPE on a null user service.
    exchange.reconnectFailure().test();
    exchange.connectionSuccess().test();
    exchange.disconnectObservable().test();
  }

  @Test
  void authenticatedModeConnectsAndHealthChecksBothSockets() {
    TestExchange exchange = exchange(true);

    assertEquals(2, exchange.services.size());
    assertNotNull(exchange.getStreamingTradeService(), "credentials enable the user trade service");
    FakeService market = exchange.services.get(0);
    FakeService user = exchange.services.get(1);
    assertEquals(API_KEY, user.apiKey, "the user service receives the L2 credential triplet");
    assertEquals(SECRET, user.secret);
    assertEquals(PASSPHRASE, user.passphrase);
    assertFalse(exchange.isAlive());

    exchange.connect().blockingAwait();
    assertEquals(List.of("connect"), market.calls);
    assertEquals(List.of("connect"), user.calls);
    assertTrue(exchange.isAlive());

    // Health includes the user socket: a closed user socket reports the whole exchange dead.
    user.open = false;
    assertFalse(exchange.isAlive(), "authenticated health covers the user socket");
    user.open = true;
    assertTrue(exchange.isAlive());

    exchange.disconnect().blockingAwait();
    assertFalse(exchange.isAlive());
    assertEquals(List.of("connect", "disconnect"), market.calls);
    assertEquals(List.of("connect", "disconnect"), user.calls);
  }
}
