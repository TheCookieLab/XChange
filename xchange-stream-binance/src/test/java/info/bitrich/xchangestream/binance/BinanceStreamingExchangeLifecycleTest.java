package info.bitrich.xchangestream.binance;

import static info.bitrich.xchangestream.core.StreamingExchange.WS_IDLE_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.lang.reflect.Field;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.config.BinanceConfiguration;
import org.knowm.xchange.binance.config.BinanceProductFamily;
import org.knowm.xchange.binance.dto.trade.BinanceCancelOrderParams;
import org.knowm.xchange.currency.CurrencyPair;

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

  @Test
  void tradingReconnectReplaysOnlySessionLogon() throws Exception {
    ExchangeSpecification specification = new ExchangeSpecification(BinanceStreamingExchange.class);
    specification.setExchangeSpecificParametersItem(WS_IDLE_TIMEOUT, 15);
    byte[] key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate().getEncoded();
    BinanceUserTradeStreamingService service =
        new BinanceUserTradeStreamingService(
            "ws://localhost", "test-key", Base64.getEncoder().encodeToString(key), specification);
    Field socket = NettyStreamingService.class.getDeclaredField("webSocketChannel");
    socket.setAccessible(true);
    EmbeddedChannel original = new EmbeddedChannel();
    EmbeddedChannel replacement = new EmbeddedChannel();
    try {
      socket.set(service, original);
      service.login();
      JsonNode originalLogin = readRequest(original);
      assertThat(originalLogin.path("method").asText()).isEqualTo("session.logon");

      Disposable pendingOrder =
          service
              .subscribeChannel(
                  "pending-cancel",
                  "order.cancel",
                  new BinanceCancelOrderParams(CurrencyPair.BTC_USDT, "123", null))
              .subscribe();
      try {
        assertThat(readRequest(original).path("method").asText()).isEqualTo("order.cancel");
        original.close();
        socket.set(service, replacement);
        service.resubscribeChannels();

        JsonNode replay = readRequest(replacement);
        assertThat(replay.path("method").asText()).isEqualTo("session.logon");
        assertThat(replay.path("id")).isEqualTo(originalLogin.path("id"));
        assertThat(replacement.outboundMessages()).isEmpty();
        service.messageHandler("{\"id\":\"" + replay.path("id").asText() + "\",\"status\":200}");
        assertThat(service.isAuthorized()).isTrue();
      } finally {
        pendingOrder.dispose();
      }
    } finally {
      service.compositeDisposable.clear();
      original.finishAndReleaseAll();
      replacement.finishAndReleaseAll();
    }
  }

  private static JsonNode readRequest(EmbeddedChannel channel) throws Exception {
    TextWebSocketFrame frame = channel.readOutbound();
    assertThat(frame).isNotNull();
    try {
      return new ObjectMapper().readTree(frame.text());
    } finally {
      frame.release();
    }
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
