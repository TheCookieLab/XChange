package info.bitrich.xchangestream.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import io.reactivex.rxjava3.observers.TestObserver;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.config.BinanceConfiguration;
import org.knowm.xchange.binance.config.BinanceProductFamily;

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

  @Test
  public void publicConnectionLivenessFollowsItsSocket() throws Exception {
    BinanceStreamingExchange exchange = createExchange(null, null);
    BinanceStreamingService market = mock(BinanceStreamingService.class);
    setService(exchange, "streamingService", market);
    when(market.isSocketOpen()).thenReturn(true, false);

    assertThat(exchange.isAlive()).isTrue();
    assertThat(exchange.isAlive()).isFalse();
  }

  @Test
  public void spotLivenessRequiresCreatedServicesToBeOpenAndAuthorized() throws Exception {
    BinanceStreamingExchange exchange = createExchange("api-key", "secret");
    BinanceStreamingService market = mock(BinanceStreamingService.class);
    BinanceUserDataSpotStreamingService userData = mock(BinanceUserDataSpotStreamingService.class);
    BinanceUserTradeStreamingService userTrade = mock(BinanceUserTradeStreamingService.class);
    setService(exchange, "streamingService", market);
    when(market.isSocketOpen()).thenReturn(true);

    // HMAC credentials do not create either WS API service.
    assertThat(exchange.isAlive()).isTrue();
    setService(exchange, "userDataSpotStreamingService", userData);
    setService(exchange, "userTradeStreamingService", userTrade);
    when(userData.isSocketOpen()).thenReturn(true);
    when(userTrade.isSocketOpen()).thenReturn(true);
    when(userData.isAuthorized()).thenReturn(true);
    when(userTrade.isAuthorized()).thenReturn(true);
    assertThat(exchange.isAlive()).isTrue();

    when(userData.isAuthorized()).thenReturn(false);
    assertThat(exchange.isAlive()).isFalse();
    when(userData.isAuthorized()).thenReturn(true);
    when(userTrade.isAuthorized()).thenReturn(false);
    assertThat(exchange.isAlive()).isFalse();
    when(userTrade.isAuthorized()).thenReturn(true);
    when(userData.isSocketOpen()).thenReturn(false);
    assertThat(exchange.isAlive()).isFalse();
    when(userData.isSocketOpen()).thenReturn(true);
    when(userTrade.isSocketOpen()).thenReturn(false);
    assertThat(exchange.isAlive()).isFalse();
  }

  @Test
  public void futuresLivenessIncludesUserDataAndOptionalAuthorizedTrading() throws Exception {
    BinanceStreamingExchange exchange =
        createExchange("api-key", "secret", BinanceProductFamily.USDM);
    BinanceStreamingService market = mock(BinanceStreamingService.class);
    BinanceUserDataFutureStreamingService userData =
        mock(BinanceUserDataFutureStreamingService.class);
    BinanceUserTradeStreamingService userTrade = mock(BinanceUserTradeStreamingService.class);
    setService(exchange, "streamingService", market);
    setService(exchange, "userDataFutureStreamingService", userData);
    when(market.isSocketOpen()).thenReturn(true);
    when(userData.isSocketOpen()).thenReturn(true);

    assertThat(exchange.isAlive()).isTrue();
    when(userData.isSocketOpen()).thenReturn(false);
    assertThat(exchange.isAlive()).isFalse();
    when(userData.isSocketOpen()).thenReturn(true);
    setService(exchange, "userTradeStreamingService", userTrade);
    when(userTrade.isSocketOpen()).thenReturn(true);
    when(userTrade.isAuthorized()).thenReturn(false);
    assertThat(exchange.isAlive()).isFalse();
    when(userTrade.isAuthorized()).thenReturn(true);
    assertThat(exchange.isAlive()).isTrue();
    when(market.isSocketOpen()).thenReturn(false);
    assertThat(exchange.isAlive()).isFalse();
  }

  private static BinanceStreamingExchange createExchange(String apiKey, String secretKey) {
    return createExchange(apiKey, secretKey, BinanceProductFamily.SPOT);
  }

  private static BinanceStreamingExchange createExchange(
      String apiKey, String secretKey, BinanceProductFamily family) {
    BinanceStreamingExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(BinanceStreamingExchange.class);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey(apiKey);
    specification.setSecretKey(secretKey);
    specification.setExchangeSpecificParametersItem(BinanceConfiguration.PRODUCT_FAMILY, family);
    exchange.applySpecification(specification);
    return exchange;
  }

  private static void setService(BinanceStreamingExchange exchange, String fieldName, Object value)
      throws ReflectiveOperationException {
    Field field = BinanceStreamingExchange.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(exchange, value);
  }
}
