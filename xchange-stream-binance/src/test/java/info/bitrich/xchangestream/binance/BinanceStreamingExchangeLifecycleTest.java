package info.bitrich.xchangestream.binance;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;

/**
 * Guards the streaming exchange lifecycle against null services for every credential/product
 * combination.
 *
 * <p>Regression: {@code isAlive()} and the connection-state observables threw
 * {@link NullPointerException} when a service was never created for the configured credentials
 * (for example HMAC keys without the WS API trading service, or spot user-data in futures mode).
 */
public class BinanceStreamingExchangeLifecycleTest {

  @Test
  public void testIsAliveWithoutConnectionIsFalseAndDoesNotThrow() {
    BinanceStreamingExchange exchange = createExchange(null, null);

    assertThat(exchange.isAlive()).isFalse();
  }

  @Test
  public void testIsAliveWithCredentialsBeforeConnectIsFalseAndDoesNotThrow() {
    BinanceStreamingExchange exchange = createExchange("api-key", "secret");

    assertThat(exchange.isAlive()).isFalse();
  }

  @Test
  public void testConnectionStateObservablesAreEmptyWithoutServices() {
    BinanceStreamingExchange exchange = createExchange("api-key", "secret");

    TestObserver<State> userDataObserver = TestObserver.create();
    exchange.connectionStateObservableUserData().subscribe(userDataObserver);
    userDataObserver.assertComplete();

    TestObserver<State> userTradeObserver = TestObserver.create();
    exchange.connectionStateObservableUserTrade().subscribe(userTradeObserver);
    userTradeObserver.assertComplete();
  }

  @Test
  public void testDisconnectBeforeConnectDoesNotThrow() {
    BinanceStreamingExchange exchange = createExchange(null, null);

    exchange.disconnect().blockingAwait();
    assertThat(exchange.isAlive()).isFalse();
  }

  private static BinanceStreamingExchange createExchange(String apiKey, String secretKey) {
    BinanceStreamingExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(BinanceStreamingExchange.class);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey(apiKey);
    specification.setSecretKey(secretKey);
    exchange.applySpecification(specification);
    return exchange;
  }
}
